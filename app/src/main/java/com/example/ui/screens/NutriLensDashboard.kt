package com.example.ui.screens

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.FoodScan
import com.example.ui.util.ImageUtil
import com.example.ui.viewmodel.NutriViewModel
import com.example.ui.viewmodel.ScanUiState
import java.util.Calendar

// Beautiful Professional Polish Healthy Vibe Colors
val ForestGreen = Color(0xFF8F4C38) // Rust / Terra Cotta Primary Brand Accent
val EmeraldGreen = Color(0xFF454D1E) // Deep Lush Olive Text
val PaleHealthyGreen = Color(0xFFE7EBD1) // Premium Soft Olive Background Accent
val SunsetOrange = Color(0xFFD35400) // Deep Sunset Orange For High-Calories
val SoftOrange = Color(0xFFF5DED8) // Warm Soft Clay Peach Pastel
val WarmGold = Color(0xFFD4AC0D) // Sophisticated Ochre Gold
val DarkCharcoal = Color(0xFF201A19) // Rich Grounded Dark Bronze/Charcoal
val SmoothGrayBackground = Color(0xFFFDF8F6) // Premium Warm Cream Milk Backdrop

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NutriLensDashboard(
    viewModel: NutriViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scanUiState by viewModel.scanUiState.collectAsStateWithLifecycle()
    val scanHistory by viewModel.scanHistory.collectAsStateWithLifecycle()

    var activeDetailScan by remember { mutableStateOf<FoodScan?>(null) }
    var showApiKeyInfo by remember { mutableStateOf(false) }

    // Calculate Today's Stats
    val todayStart = remember(scanHistory) {
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
    val todayScans = scanHistory.filter { it.timestamp >= todayStart }
    val todayCalories = todayScans.sumOf { it.calories }
    val healthyMealsCount = todayScans.count { it.isHealthy }

    // Launchers for capturing images
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        if (bitmap != null) {
            viewModel.scanFoodImage(bitmap)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            cameraLauncher.launch(null)
        } else {
            android.widget.Toast.makeText(
                context,
                "A permissão de câmera é necessária para tirar fotos dos seus pratos.",
                android.widget.Toast.LENGTH_LONG
            ).show()
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            val bitmap = ImageUtil.getBitmapFromUri(context, uri)
            if (bitmap != null) {
                viewModel.scanFoodImage(bitmap)
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = SmoothGrayBackground,
        topBar = {
            NutriHeader(
                isApiKeyOk = viewModel.isApiKeyAvailable,
                onInfoClick = { showApiKeyInfo = true }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 72.dp)
            ) {
                // 1. Dashboard Goals Summary Bar
                item {
                    DailyStatsOverview(
                        totalCalories = todayCalories,
                        totalScans = todayScans.size,
                        healthyCount = healthyMealsCount
                    )
                }

                // 2. Main Scan Workspace
                item {
                    ScanAreaWorkspace(
                        uiState = scanUiState,
                        onTakePhoto = {
                            val isGranted = androidx.core.content.ContextCompat.checkSelfPermission(
                                context,
                                android.Manifest.permission.CAMERA
                            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                            
                            if (isGranted) {
                                cameraLauncher.launch(null)
                            } else {
                                permissionLauncher.launch(android.Manifest.permission.CAMERA)
                            }
                        },
                        onPickGallery = { galleryLauncher.launch("image/*") },
                        onReset = { viewModel.resetState() }
                    )
                }

                // 3. Interactive Model Food Plates Tray (Testing Sandbox)
                item {
                    ModelPlatesTray(
                        onSelectPreset = { name, prompt, emoji ->
                            viewModel.scanPresetFood(name, prompt, emoji)
                        },
                        isLoading = scanUiState is ScanUiState.Loading
                    )
                }

                // 4. Stored Scan History (Room DB)
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Diário de Pratos",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = DarkCharcoal
                        )
                        if (scanHistory.isNotEmpty()) {
                            TextButton(
                                onClick = { viewModel.clearHistory() },
                                colors = ButtonDefaults.textButtonColors(contentColor = SunsetOrange)
                            ) {
                                Icon(Icons.Rounded.DeleteForever, contentDescription = "Limpar Tudo", modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Limpar Diário", fontSize = 12.sp)
                            }
                        }
                    }
                }

                if (scanHistory.isEmpty()) {
                    item {
                        EmptyHistoryState()
                    }
                } else {
                    items(
                        items = scanHistory,
                        key = { it.id }
                    ) { scan ->
                        HistoryScanCard(
                            scan = scan,
                            onCardClick = { activeDetailScan = scan },
                            onDeleteClick = { viewModel.deleteScan(scan.id) }
                        )
                    }
                }
            }

            // 5. Sliding Analysis Dialog view for custom history selections
            activeDetailScan?.let { scan ->
                FoodDetailsDialog(
                    scan = scan,
                    onDismiss = { activeDetailScan = null }
                )
            }

            // 6. API Key explanation popup
            if (showApiKeyInfo) {
                ApiKeyInfoDialog(onDismiss = { showApiKeyInfo = false })
            }
        }
    }
}

// APP BAR COMPOSABLE
@Composable
fun NutriHeader(
    isApiKeyOk: Boolean,
    onInfoClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = ForestGreen,
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .statusBarsPadding()
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.AppShortcut,
                        contentDescription = "NutriLens Logo",
                        tint = Color.White,
                        modifier = Modifier.size(26.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "NutriLens",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White
                    )
                }
                Text(
                    text = "Seu scanner inteligente de nutrição",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.8f)
                )
            }

            IconButton(onClick = onInfoClick) {
                Icon(
                    imageVector = if (isApiKeyOk) Icons.Rounded.CheckCircle else Icons.Rounded.Warning,
                    contentDescription = "API Status",
                    tint = if (isApiKeyOk) Color(0xFFC8E6C9) else SoftOrange,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

// DAILY STATS OVERVIEW
@Composable
fun DailyStatsOverview(
    totalCalories: Int,
    totalScans: Int,
    healthyCount: Int
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Resumo Alimentar de Hoje",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = ForestGreen
            )
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Calorie Stats Column
                Column(modifier = Modifier.weight(1f)) {
                    Text("Total Estimado", fontSize = 11.sp, color = Color.Gray)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.LocalFireDepartment, contentDescription = "Calorias", tint = SunsetOrange, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "$totalCalories kcal",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = DarkCharcoal
                        )
                    }
                }

                // Divider line
                VerticalDivider(
                    modifier = Modifier
                        .height(36.dp)
                        .padding(horizontal = 12.dp)
                )

                // Dishes Scanner Count
                Column(modifier = Modifier.weight(1f)) {
                    Text("Refeições no Diário", fontSize = 11.sp, color = Color.Gray)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.RestaurantMenu, contentDescription = "Pratos", tint = ForestGreen, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "$totalScans prato(s)",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = DarkCharcoal
                        )
                    }
                }

                // Balance Index
                VerticalDivider(
                    modifier = Modifier
                        .height(36.dp)
                        .padding(horizontal = 12.dp)
                )

                Column(modifier = Modifier.weight(1f)) {
                    Text("Taxa Saudável", fontSize = 11.sp, color = Color.Gray)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val percent = if (totalScans > 0) (healthyCount * 100) / totalScans else 0
                        Icon(
                            imageVector = if (percent >= 60 || totalScans == 0) Icons.Rounded.ThumbUp else Icons.Rounded.PriorityHigh,
                            contentDescription = "Taxa saudável",
                            tint = if (percent >= 60) EmeraldGreen else SunsetOrange,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "$percent%",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = DarkCharcoal
                        )
                    }
                }
            }

            // Quick motivational meter progress bar
            if (totalScans > 0) {
                Spacer(modifier = Modifier.height(12.dp))
                val ratio = healthyCount.toFloat() / totalScans
                LinearProgressIndicator(
                    progress = { ratio },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(CircleShape),
                    color = EmeraldGreen,
                    trackColor = PaleHealthyGreen
                )
                Text(
                    text = if (ratio >= 0.7f) "Excelente escolha de nutrientes hoje! Continue assim."
                    else if (ratio >= 0.4f) "Seu equilíbrio hoje está mediano. Tente comer mais vegetais na próxima."
                    else "Seus pratos hoje pedem mais vitaminas estimulantes!",
                    fontSize = 10.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }
    }
}

// MAIN ACTIVE AREA (SCANNER TRIGGER)
@Composable
fun ScanAreaWorkspace(
    uiState: ScanUiState,
    onTakePhoto: () -> Unit,
    onPickGallery: () -> Unit,
    onReset: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Scanner de Comida IA",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = DarkCharcoal,
                modifier = Modifier.align(Alignment.Start)
            )
            Spacer(modifier = Modifier.height(12.dp))

            when (uiState) {
                is ScanUiState.Idle -> {
                    // Frame showing a modern camera target box
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .background(PaleHealthyGreen, RoundedCornerShape(16.dp))
                            .border(
                                width = 2.dp,
                                color = EmeraldGreen.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(16.dp)
                            )
                            .clickable { onTakePhoto() },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Rounded.CameraAlt,
                                contentDescription = "Cam",
                                tint = ForestGreen,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Tire uma foto ou submeta o prato",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = ForestGreen
                            )
                            Text(
                                "Toque para abrir a câmera instantânea",
                                fontSize = 10.sp,
                                color = Color.Gray
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = onTakePhoto,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("camera_btn"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = ForestGreen)
                        ) {
                            Icon(Icons.Rounded.CameraAlt, contentDescription = "Camera")
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Tirar Foto", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }

                        Button(
                            onClick = onPickGallery,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("gallery_btn"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = ForestGreen),
                            border = BorderStroke(1.dp, ForestGreen)
                        ) {
                            Icon(Icons.Rounded.PhotoLibrary, contentDescription = "Gallery")
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Galeria", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                }

                is ScanUiState.Loading -> {
                    // PULSING RADAR ANIMATION YIELDS THE SCANNER FEELING!
                    val infiniteTransition = rememberInfiniteTransition(label = "scan_beam")
                    val pulseScale by infiniteTransition.animateFloat(
                        initialValue = 0.85f,
                        targetValue = 1.05f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(1200, easing = LinearEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "pulse"
                    )

                    val beamOffset by infiniteTransition.animateFloat(
                        initialValue = 0f,
                        targetValue = 160.dp.value,
                        animationSpec = infiniteRepeatable(
                            animation = tween(2200, easing = FastOutSlowInEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "beam"
                    )

                    var tickerIndex by remember { mutableStateOf(0) }
                    val tickerMessages = listOf(
                        "Identificando comida no prato... 🥗",
                        "Estimando volume e calorias... ⚡",
                        "Calculando distribuição de minerais e vitaminas... 🧪",
                        "Buscando déficits nutricionais gerais... 🔎",
                        "Gerando estratégias de equilíbrio de dieta... ✨"
                    )

                    LaunchedEffect(Unit) {
                        while (true) {
                            kotlinx.coroutines.delay(2200)
                            tickerIndex = (tickerIndex + 1) % tickerMessages.size
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .background(Color(0xFF2E7D32).copy(alpha = 0.05f), RoundedCornerShape(16.dp))
                            .border(width = 1.dp, color = MaterialTheme.colorScheme.outlineVariant, shape = RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(16.dp)
                        ) {
                            // Pulsing glowing center icon
                            Box(
                                modifier = Modifier
                                    .size((72 * pulseScale).dp)
                                    .background(PaleHealthyGreen, CircleShape)
                                    .drawBehind {
                                        drawCircle(
                                            color = EmeraldGreen.copy(alpha = 0.3f),
                                            radius = size.minDimension / 1.5f * pulseScale
                                        )
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Rounded.CloudSync,
                                    contentDescription = "Loading",
                                    tint = ForestGreen,
                                    modifier = Modifier.size(36.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(20.dp))

                            CircularProgressIndicator(
                                strokeWidth = 3.dp,
                                modifier = Modifier.size(24.dp),
                                color = ForestGreen
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            // Animated Ticker text
                            AnimatedContent(
                                targetState = tickerMessages[tickerIndex],
                                transitionSpec = {
                                    slideInVertically { height -> height } + fadeIn() togetherWith
                                            slideOutVertically { height -> -height } + fadeOut()
                                },
                                label = "ticker"
                            ) { phrase ->
                                Text(
                                    text = phrase,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = DarkCharcoal,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }

                is ScanUiState.Success -> {
                    val scan = uiState.scan
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Success header
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .background(PaleHealthyGreen, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Rounded.AutoAwesome, contentDescription = "Awesome", tint = ForestGreen, modifier = Modifier.size(16.dp))
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Análise Alimentar Concluída!",
                                style = LocalTextStyle.current.copy(fontWeight = FontWeight.Bold, fontSize = 15.sp, color = ForestGreen)
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Large Display Card
                        ScanDetailPanel(scan = scan)

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = onReset,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .testTag("scan_new_btn"),
                            colors = ButtonDefaults.buttonColors(containerColor = ForestGreen)
                        ) {
                            Icon(Icons.Rounded.QrCodeScanner, contentDescription = "Scan")
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Escanear Mais Alimentos", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                is ScanUiState.Error -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Rounded.SentimentDissatisfied, contentDescription = "Erro", tint = SunsetOrange, modifier = Modifier.size(52.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Erro na Análise",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = SunsetOrange
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = uiState.message,
                            fontSize = 12.sp,
                            color = Color.DarkGray,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = onReset,
                            colors = ButtonDefaults.buttonColors(containerColor = SunsetOrange),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Tentar Novamente", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

// FOOD DETAIL COMPONENT PANEL
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ScanDetailPanel(scan: FoodScan) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SmoothGrayBackground.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color.Gray.copy(alpha = 0.15f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Food Name & Calorie Count
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = scan.foodName,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = DarkCharcoal,
                    modifier = Modifier.weight(1f)
                )

                Box(
                    modifier = Modifier
                        .background(SunsetOrange.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.LocalFireDepartment, contentDescription = "Kcal", tint = SunsetOrange, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${scan.calories} kcal",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Black,
                            color = SunsetOrange
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // IsHealthy Check
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (scan.isHealthy) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = if (scan.isHealthy) Icons.Rounded.Verified else Icons.Rounded.Block,
                        contentDescription = "Status de saúde",
                        tint = if (scan.isHealthy) ForestGreen else SunsetOrange,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = if (scan.isHealthy) "Prato Saudável" else "Prato com Ressalvas / Calórico",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = if (scan.isHealthy) ForestGreen else SunsetOrange
                        )
                        Text(
                            text = scan.healthExplanation,
                            fontSize = 11.sp,
                            color = Color.DarkGray,
                            lineHeight = 14.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Vitamins Present (Presentes)
            Text(
                "Vitaminas e Nutrientes Encontrados:",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = ForestGreen
            )
            Spacer(modifier = Modifier.height(6.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val presentList = scan.vitaminsPresent.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                if (presentList.isEmpty()) {
                    Text("Nenhum destaque mapeado", fontSize = 11.sp, color = Color.Gray)
                } else {
                    presentList.forEach { vit ->
                        SuggestionChip(
                            onClick = {},
                            label = { Text(vit, fontSize = 11.sp) },
                            icon = { Icon(Icons.Rounded.Check, contentDescription = "ok", modifier = Modifier.size(12.dp), tint = ForestGreen) },
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                labelColor = ForestGreen,
                                containerColor = PaleHealthyGreen
                            ),
                            border = BorderStroke(0.5.dp, ForestGreen.copy(alpha = 0.3f))
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Missing Vitamins (Nutrientes em falta!)
            Text(
                "Em falta neste prato (Nutrientes ausentes):",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = SunsetOrange
            )
            Spacer(modifier = Modifier.height(6.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val missingList = scan.vitaminsMissing.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                if (missingList.isEmpty()) {
                    Text("Tudo equilibrado neste prato!", fontSize = 11.sp, color = Color.Gray)
                } else {
                    missingList.forEach { vit ->
                        SuggestionChip(
                            onClick = {},
                            label = { Text(vit, fontSize = 11.sp) },
                            icon = { Icon(Icons.Rounded.Warning, contentDescription = "warning", modifier = Modifier.size(12.dp), tint = SunsetOrange) },
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                labelColor = SunsetOrange,
                                containerColor = Color(0xFFFFEBEE)
                            ),
                            border = BorderStroke(0.5.dp, SunsetOrange.copy(alpha = 0.3f))
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Improvements Advice
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, WarmGold.copy(alpha = 0.4f))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.TipsAndUpdates, contentDescription = "Ideias", tint = WarmGold, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Como Equilibrar Melhor:",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = DarkCharcoal
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = scan.improvements,
                        fontSize = 11.sp,
                        color = Color.DarkGray,
                        lineHeight = 15.sp
                    )
                }
            }
        }
    }
}

// INTERACTIVE TEST SANDBOX CARD TRAY
@Composable
fun ModelPlatesTray(
    onSelectPreset: (name: String, prompt: String, emoji: String) -> Unit,
    isLoading: Boolean
) {
    val presets = listOf(
        PresetPlate("Prato Executivo", "🥗", "Filé de Frango Grelhado, Arroz Integral, Feijão e Salada", "Frango grelhado de 150g, arroz integral, feijão e salada colorida farta com tomate."),
        PresetPlate("Super Burguer", "🍔", "Hambúrguer com Queijo Cheddar e Batata Frita Grande", "Hambúrguer duplo de carne frita com bacon, cheddar cremoso e fritas com sal."),
        PresetPlate("Massa Italiana", "🍝", "Espaguete ao Sugo com Molho de Carne Moída", "Espaguete cozido farto com molho bolonhesa clássico caseiro e queijo ralado."),
        PresetPlate("Sobremesa", "🍰", "Bolo Fudge de Chocolate com Calda de Brigadeiro", "Bolo fofo de chocolate com recheio denso de brigadeiro e raspas doces do chef.")
    )

    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(
            text = "Teste Fácil: Selecione um Prato do Chef",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = DarkCharcoal,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
        )

        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 14.dp)
        ) {
            presets.forEach { item ->
                Card(
                    modifier = Modifier
                        .width(160.dp)
                        .padding(6.dp)
                        .clickable(enabled = !isLoading) {
                            onSelectPreset(item.name, item.desc, item.emoji)
                        },
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = item.emoji,
                            fontSize = 32.sp,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                        Text(
                            text = item.name,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = ForestGreen,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = item.shortDesc,
                            fontSize = 10.sp,
                            color = Color.Gray,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            lineHeight = 12.sp,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.height(26.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .background(PaleHealthyGreen, RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 3.dp)
                        ) {
                            Text(
                                "Análise Rápida",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = ForestGreen
                            )
                        }
                    }
                }
            }
        }
    }
}

data class PresetPlate(
    val name: String,
    val emoji: String,
    val shortDesc: String,
    val desc: String
)

// EMPTY HISTORY STATE LOGS
@Composable
fun EmptyHistoryState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Rounded.NoFood,
            contentDescription = "Diário Vazio",
            tint = Color.LightGray,
            modifier = Modifier.size(56.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Diário Mapeado Vazio",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = Color.Gray
        )
        Text(
            text = "Sua refeições escaneadas aparecerão organizadas aqui. Tire uma foto ou clique em um prato acima para testar!",
            fontSize = 11.sp,
            color = Color.Gray,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp).padding(top = 4.dp)
        )
    }
}

// TIMELINE ITEM FOR HISTORY JOURNAL
@Composable
fun HistoryScanCard(
    scan: FoodScan,
    onCardClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp)
            .clickable { onCardClick() },
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left Image representation (either loaded base64 of photo OR preset emoji)
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(PaleHealthyGreen),
                contentAlignment = Alignment.Center
            ) {
                if (scan.imageBase64 != null && scan.imageBase64.startsWith("EMOJI:")) {
                    val emoji = scan.imageBase64.substringAfter("EMOJI:")
                    Text(text = emoji, fontSize = 24.sp)
                } else if (scan.imageBase64 != null) {
                    val bitmap = remember(scan.imageBase64) {
                        ImageUtil.getBitmapFromBase64(scan.imageBase64)
                    }
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "Thumbnail Comida",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(Icons.Rounded.Fastfood, contentDescription = "Comida", tint = ForestGreen)
                    }
                } else {
                    Icon(Icons.Rounded.Fastfood, contentDescription = "Comida", tint = ForestGreen)
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Body Columns
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = scan.foodName,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = DarkCharcoal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    // Is Healthy badge dot
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(if (scan.isHealthy) EmeraldGreen else SunsetOrange, CircleShape)
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "🔥 ${scan.calories} kcal",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = SunsetOrange
                    )

                    Text(
                        text = scan.formattedDate,
                        fontSize = 10.sp,
                        color = Color.Gray
                    )
                }
            }

            // Right Actions
            IconButton(onClick = onDeleteClick) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = "Excluir Scan",
                    tint = Color.LightGray,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

// FOOD INDIVIDUAL HISTORY DETAILS OVERLAY SCREEN/DIALOG
@Composable
fun FoodDetailsDialog(
    scan: FoodScan,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
            shape = RoundedCornerShape(24.dp),
            color = Color.White
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                // Header with name
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = scan.foodName,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = DarkCharcoal
                        )
                        Text(
                            text = "Escaneado em ${scan.formattedDate}",
                            fontSize = 10.sp,
                            color = Color.Gray
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(PaleHealthyGreen),
                        contentAlignment = Alignment.Center
                    ) {
                        if (scan.imageBase64 != null && scan.imageBase64.startsWith("EMOJI:")) {
                            val emoji = scan.imageBase64.substringAfter("EMOJI:")
                            Text(text = emoji, fontSize = 24.sp)
                        } else if (scan.imageBase64 != null) {
                            val bitmap = remember(scan.imageBase64) {
                                ImageUtil.getBitmapFromBase64(scan.imageBase64)
                            }
                            if (bitmap != null) {
                                Image(
                                    bitmap = bitmap.asImageBitmap(),
                                    contentDescription = "Th",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Icon(Icons.Rounded.Dining, contentDescription = "dining", tint = ForestGreen)
                            }
                        } else {
                            Icon(Icons.Rounded.Dining, contentDescription = "dining", tint = ForestGreen)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Scrollable container
                Box(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                ) {
                    ScanDetailPanel(scan = scan)
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ForestGreen)
                ) {
                    Text("Fechar Histórico", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// GOOGLE AI STUDIO API KEY HELP GUIDE
@Composable
fun ApiKeyInfoDialog(onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
            shape = RoundedCornerShape(24.dp),
            color = Color.White
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Rounded.VerifiedUser,
                    contentDescription = "Chave Secreta",
                    tint = ForestGreen,
                    modifier = Modifier.size(52.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Configuração de Chave de API",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = DarkCharcoal
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Este protótipo local utiliza a Inteligência Artificial do Gemini para analisar as fotos dos seus pratos de comida.\n\n" +
                            "Para funcionar corretamente, você deve cadastrar a chave de API nos Segredos do seu ambiente:\n\n" +
                            "1. Abra os Segredos de Desenvolvimento do Google AI Studio.\n" +
                            "2. Adicione ou preencha a chave 'GEMINI_API_KEY' com o seu token pessoal de desenvolvimento gratuito.\n" +
                            "3. Reinicie a visualização do app.",
                    fontSize = 11.sp,
                    color = Color.DarkGray,
                    textAlign = TextAlign.Center,
                    lineHeight = 15.sp,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )

                Spacer(modifier = Modifier.height(18.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ForestGreen)
                ) {
                    Text("Entendido", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
