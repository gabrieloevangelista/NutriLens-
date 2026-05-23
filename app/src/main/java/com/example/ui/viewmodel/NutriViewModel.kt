package com.example.ui.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.data.AppDatabase
import com.example.data.FoodScan
import com.example.data.FoodScanRepository
import com.example.network.Content
import com.example.network.GenerateContentRequest
import com.example.network.GeminiMealAnalysis
import com.example.network.GenerationConfig
import com.example.network.InlineData
import com.example.network.Part
import com.example.network.RetrofitClient
import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

sealed interface ScanUiState {
    object Idle : ScanUiState
    object Loading : ScanUiState
    data class Success(val scan: FoodScan) : ScanUiState
    data class Error(val message: String) : ScanUiState
}

class NutriViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application)
    private val repository = FoodScanRepository(database.foodScanDao())

    // Observe local scan history
    val scanHistory: StateFlow<List<FoodScan>> = repository.allScans
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Current screen scanning state
    val scanUiState = MutableStateFlow<ScanUiState>(ScanUiState.Idle)

    // API Key State Check
    val isApiKeyAvailable: Boolean
        get() = BuildConfig.GEMINI_API_KEY.isNotEmpty() && BuildConfig.GEMINI_API_KEY != "MY_GEMINI_API_KEY"

    fun resetState() {
        scanUiState.value = ScanUiState.Idle
    }

    /**
     * Scans an image bitmap of food, queries the Gemini model, and saves the result.
     */
    fun scanFoodImage(bitmap: Bitmap) {
        viewModelScope.launch {
            scanUiState.value = ScanUiState.Loading

            val apiCheckKey = BuildConfig.GEMINI_API_KEY
            if (apiCheckKey.isEmpty() || apiCheckKey == "MY_GEMINI_API_KEY") {
                scanUiState.value = ScanUiState.Error(
                    "Chave de API do Gemini não configurada! Insira sua GEMINI_API_KEY nas configurações de segredos do AI Studio."
                )
                return@launch
            }

            try {
                // 1. Prepare images (Full size base64 for API call, Thumbnail base64 for local database persistence)
                val fullBase64 = withContext(Dispatchers.Default) {
                    bitmap.toBase64(quality = 75, maxDimension = 800)
                }
                val thumbBase64 = withContext(Dispatchers.Default) {
                    bitmap.toBase64(quality = 60, maxDimension = 300)
                }

                // 2. Formulate nutritional request
                val prompt = "Analise o prato de comida presente na imagem. Estime as calorias e preencha o JSON de resposta corretamente em Português do Brasil."

                val systemPromptText = """
                    Você é o NutriLens, especialista em nutrição saudável.
                    Sua tarefa é analisar a imagem de comida enviada e retornar exatamente uma resposta JSON estruturada seguindo o formato:
                    {
                      "foodName": "Nome do prato ou itens identificados",
                      "calories": 350,
                      "isHealthy": true,
                      "healthExplanation": "Uma explicação concisa de 2-3 frases dizendo porque é saudável ou não.",
                      "vitaminsPresent": ["Vitamina A", "Vitamina C"],
                      "vitaminsMissing": ["Vitamina D", "Fibras", "Cálcio"],
                      "improvements": "Explicação curta indicando melhoras, por exemplo: adicione vegetais folhosos e sementes."
                    }
                    IMPORTANTE: Retorne APENAS o JSON. Não inclua blocos de formatação markdown adicionais ou explicações adicionais pré ou pós JSON. Garanta que o JSON seja perfeitamente válido.
                """.trimIndent()

                val request = GenerateContentRequest(
                    contents = listOf(
                        Content(
                            parts = listOf(
                                Part(text = prompt),
                                Part(inlineData = InlineData(mimeType = "image/jpeg", data = fullBase64))
                            )
                        )
                    ),
                    generationConfig = GenerationConfig(
                        responseMimeType = "application/json",
                        temperature = 0.4f
                    ),
                    systemInstruction = Content(
                        parts = listOf(Part(text = systemPromptText))
                    )
                )

                // 3. Make server API call
                val response = withContext(Dispatchers.IO) {
                    RetrofitClient.service.generateContent(apiCheckKey, request)
                }

                val jsonResponseText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                if (jsonResponseText == null) {
                    scanUiState.value = ScanUiState.Error("Não foi possível obter resposta da Inteligência Artificial.")
                    return@launch
                }

                Log.d("NutriLens", "AI Raw Output: $jsonResponseText")

                // 4. Parse the Structured JSON Response
                val parsedResult = withContext(Dispatchers.Default) {
                    parseGeminiResponse(jsonResponseText)
                }

                if (parsedResult == null) {
                    scanUiState.value = ScanUiState.Error("Erro ao processar as informações nutricionais retornadas.")
                    return@launch
                }

                // 5. Build entity and write to DB
                val foodScan = FoodScan(
                    foodName = parsedResult.foodName,
                    calories = parsedResult.calories,
                    isHealthy = parsedResult.isHealthy,
                    healthExplanation = parsedResult.healthExplanation,
                    vitaminsPresent = parsedResult.vitaminsPresent.joinToString(", "),
                    vitaminsMissing = parsedResult.vitaminsMissing.joinToString(", "),
                    improvements = parsedResult.improvements,
                    imageBase64 = thumbBase64,
                    timestamp = System.currentTimeMillis()
                )

                // Save to Room DB persistence
                withContext(Dispatchers.IO) {
                    repository.insert(foodScan)
                }

                scanUiState.value = ScanUiState.Success(foodScan)

            } catch (e: Exception) {
                Log.e("NutriLens", "Error during scanning", e)
                scanUiState.value = ScanUiState.Error("Ops! Falha de rede ou de processamento: ${e.localizedMessage ?: "Erro desconhecido"}")
            }
        }
    }

    /**
     * Scans a preset food item by sending its descriptive prompt to Gemini and setting a custom emoji image marker.
     */
    fun scanPresetFood(foodName: String, promptDesc: String, emoji: String) {
        viewModelScope.launch {
            scanUiState.value = ScanUiState.Loading

            val apiCheckKey = BuildConfig.GEMINI_API_KEY
            if (apiCheckKey.isEmpty() || apiCheckKey == "MY_GEMINI_API_KEY") {
                scanUiState.value = ScanUiState.Error(
                    "Chave de API do Gemini não configurada! Insira sua GEMINI_API_KEY nas configurações de segredos do AI Studio."
                )
                return@launch
            }

            try {
                val prompt = "Analise o seguinte prato: $foodName. Descrição detalhada: $promptDesc. Estime as calorias e preencha o JSON de resposta corretamente em Português do Brasil."

                val systemPromptText = """
                    Você é o NutriLens, especialista em nutrição saudável.
                    Sua tarefa é analisar o prato de comida descrito e retornar exatamente uma resposta JSON estruturada seguindo o formato:
                    {
                      "foodName": "Nome do prato ou itens identificados",
                      "calories": 350,
                      "isHealthy": true,
                      "healthExplanation": "Uma explicação concisa de 2-3 frases dizendo porque é saudável ou não.",
                      "vitaminsPresent": ["Vitamina A", "Vitamina C"],
                      "vitaminsMissing": ["Vitamina D", "Fibras", "Cálcio"],
                      "improvements": "Explicação curta indicando melhoras, por exemplo: adicione vegetais folhosos e sementes."
                    }
                    IMPORTANTE: Retorne APENAS o JSON. Não inclua blocos de formatação markdown adicionais ou explicações adicionais pré ou pós JSON. Garanta que o JSON seja perfeitamente válido.
                """.trimIndent()

                val request = GenerateContentRequest(
                    contents = listOf(
                        Content(parts = listOf(Part(text = prompt)))
                    ),
                    generationConfig = GenerationConfig(
                        responseMimeType = "application/json",
                        temperature = 0.4f
                    ),
                    systemInstruction = Content(
                        parts = listOf(Part(text = systemPromptText))
                    )
                )

                // Make server API call
                val response = withContext(Dispatchers.IO) {
                    RetrofitClient.service.generateContent(apiCheckKey, request)
                }

                val jsonResponseText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                if (jsonResponseText == null) {
                    scanUiState.value = ScanUiState.Error("Não foi possível obter resposta da Inteligência Artificial.")
                    return@launch
                }

                val parsedResult = withContext(Dispatchers.Default) {
                    parseGeminiResponse(jsonResponseText)
                }

                if (parsedResult == null) {
                    scanUiState.value = ScanUiState.Error("Erro ao processar as informações nutricionais retornadas.")
                    return@launch
                }

                // Prepare Entity
                val foodScan = FoodScan(
                    foodName = parsedResult.foodName,
                    calories = parsedResult.calories,
                    isHealthy = parsedResult.isHealthy,
                    healthExplanation = parsedResult.healthExplanation,
                    vitaminsPresent = parsedResult.vitaminsPresent.joinToString(", "),
                    vitaminsMissing = parsedResult.vitaminsMissing.joinToString(", "),
                    improvements = parsedResult.improvements,
                    imageBase64 = "EMOJI:$emoji", // Custom preset marker
                    timestamp = System.currentTimeMillis()
                )

                withContext(Dispatchers.IO) {
                    repository.insert(foodScan)
                }

                scanUiState.value = ScanUiState.Success(foodScan)

            } catch (e: Exception) {
                Log.e("NutriLens", "Error during scanning preset", e)
                scanUiState.value = ScanUiState.Error("Ops! Falha de rede ou de processamento: ${e.localizedMessage ?: "Erro desconhecido"}")
            }
        }
    }

    /**
     * Deletes a historic scan from Room DB
     */
    fun deleteScan(id: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteById(id)
        }
    }

    /**
     * Clears scan history database
     */
    fun clearHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.clearAll()
        }
    }

    /**
     * Clean and parse json response safely
     */
    private fun parseGeminiResponse(rawText: String): GeminiMealAnalysis? {
        // Strip markdown backticks if returned (sometimes models do this despite config)
        var cleanedText = rawText.trim()
        if (cleanedText.startsWith("```")) {
            cleanedText = cleanedText.removePrefix("```")
            if (cleanedText.startsWith("json")) {
                cleanedText = cleanedText.removePrefix("json")
            }
            if (cleanedText.endsWith("```")) {
                cleanedText = cleanedText.removeSuffix("```")
            }
            cleanedText = cleanedText.trim()
        }

        return try {
            val adapter = RetrofitClient.moshiInstance.adapter(GeminiMealAnalysis::class.java)
            adapter.fromJson(cleanedText)
        } catch (e: Exception) {
            Log.e("NutriLens", "JSON parsing failure: $cleanedText", e)
            null
        }
    }

    /**
     * Extension to scale and encode Bitmap to Base64
     */
    private fun Bitmap.toBase64(quality: Int, maxDimension: Int): String {
        // Resize to avoid high bandwidth consumption and memory limits
        val originalWidth = width
        val originalHeight = height
        var newWidth = originalWidth
        var newHeight = originalHeight

        if (originalWidth > maxDimension || originalHeight > maxDimension) {
            if (originalWidth > originalHeight) {
                newWidth = maxDimension
                newHeight = (maxDimension.toFloat() / originalWidth * originalHeight).toInt()
            } else {
                newHeight = maxDimension
                newWidth = (maxDimension.toFloat() / originalHeight * originalWidth).toInt()
            }
        }

        val resizedBitmap = Bitmap.createScaledBitmap(this, newWidth, newHeight, true)
        val outputStream = ByteArrayOutputStream()
        resizedBitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
        val byteArray = outputStream.toByteArray()
        return Base64.encodeToString(byteArray, Base64.NO_WRAP)
    }
}
