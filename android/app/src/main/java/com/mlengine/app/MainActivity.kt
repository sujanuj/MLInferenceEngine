package com.mlengine.app

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background) {
                    AppNavigation()
                }
            }
        }
    }
}

@Composable
fun AppNavigation(viewModel: MainViewModel = viewModel()) {
    var showDashboard by remember { mutableStateOf(false) }
    if (showDashboard) {
        MetricsDashboardScreen(viewModel = viewModel, onBack = { showDashboard = false })
    } else {
        MLInferenceApp(viewModel = viewModel, onShowDashboard = { showDashboard = true })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MLInferenceApp(viewModel: MainViewModel = viewModel(), onShowDashboard: () -> Unit = {}) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? -> viewModel.setSelectedImage(uri) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("ML Inference Engine", fontWeight = FontWeight.Bold)
                        Text(
                            text = if (uiState.serverOnline) "Server Online" else "Server Offline",
                            fontSize = 12.sp,
                            color = if (uiState.serverOnline) Color(0xFF4CAF50) else Color(0xFFF44336)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.checkServer(); viewModel.refreshMetrics() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                    IconButton(onClick = onShowDashboard) {
                        Icon(Icons.Default.Analytics, contentDescription = "Dashboard")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding)
                .verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Network Simulator Card
            NetworkSimulatorCard(
                currentQuality = uiState.networkQuality,
                networkLabel = uiState.networkLabel,
                onQualitySelected = { viewModel.setNetworkQuality(it) }
            )

            ModeSelector(selectedMode = uiState.selectedMode, onModeSelected = viewModel::setMode)
            ImagePickerCard(imageUri = uiState.selectedImageUri, onPickImage = { imagePicker.launch("image/*") })
            Button(
                onClick = { viewModel.runInference(context) },
                enabled = uiState.selectedImageUri != null && !uiState.isLoading && uiState.serverOnline,
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp)); Text("Running Inference...")
                } else {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(8.dp)); Text("Run Inference")
                }
            }
            uiState.error?.let { error ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.width(8.dp))
                        Text(error, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                    }
                }
            }
            uiState.result?.let { result -> ResultCard(result) }
            uiState.metrics?.let { metrics ->
                OutlinedButton(onClick = onShowDashboard, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Analytics, contentDescription = null)
                    Spacer(Modifier.width(8.dp)); Text("View Live Metrics Dashboard")
                }
                MetricsDashboard(metrics)
            }
        }
    }
}

@Composable
fun NetworkSimulatorCard(
    currentQuality: String,
    networkLabel: String,
    onQualitySelected: (String) -> Unit
) {
    val qualities = listOf(
        Triple("GOOD", "Good", Color(0xFF4CAF50)),
        Triple("POOR", "Poor", Color(0xFFFF9800)),
        Triple("TERRIBLE", "Terrible", Color(0xFFF44336))
    )

    Card(
        colors = CardDefaults.cardColors(
            containerColor = when (currentQuality) {
                "POOR" -> Color(0xFFFF9800).copy(alpha = 0.1f)
                "TERRIBLE" -> Color(0xFFF44336).copy(alpha = 0.1f)
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.NetworkCheck,
                    contentDescription = null,
                    tint = when (currentQuality) {
                        "POOR" -> Color(0xFFFF9800)
                        "TERRIBLE" -> Color(0xFFF44336)
                        else -> Color(0xFF4CAF50)
                    }
                )
                Spacer(Modifier.width(8.dp))
                Text("Network Simulator", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "Simulate bad network to watch adaptive routing switch models",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                qualities.forEach { (quality, label, color) ->
                    FilterChip(
                        selected = currentQuality == quality,
                        onClick = { onQualitySelected(quality) },
                        label = { Text(label, fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = color.copy(alpha = 0.2f),
                            selectedLabelColor = color
                        )
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                networkLabel,
                fontSize = 11.sp,
                color = when (currentQuality) {
                    "POOR" -> Color(0xFFFF9800)
                    "TERRIBLE" -> Color(0xFFF44336)
                    else -> Color(0xFF4CAF50)
                },
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun ModeSelector(selectedMode: String, onModeSelected: (String) -> Unit) {
    Card {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Inference Mode", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("fast", "accurate", "adaptive").forEach { mode ->
                    FilterChip(selected = selectedMode == mode, onClick = { onModeSelected(mode) },
                        label = { Text(mode.replaceFirstChar { it.uppercase() }) })
                }
            }
            if (selectedMode == "adaptive") {
                Spacer(Modifier.height(4.dp))
                Text("Routes to fast or accurate model based on latency budget",
                    fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun ImagePickerCard(imageUri: Uri?, onPickImage: () -> Unit) {
    Card(onClick = onPickImage, modifier = Modifier.fillMaxWidth()) {
        Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
            if (imageUri != null) {
                AsyncImage(model = imageUri, contentDescription = "Selected image",
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop)
            } else {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.AddPhotoAlternate, contentDescription = null,
                        modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(8.dp))
                    Text("Tap to select an image", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
fun ResultCard(result: InferenceResult) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("Result", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(Modifier.weight(1f))
                AssistChip(onClick = {}, label = { Text(result.modelType, fontSize = 11.sp) })
            }
            Spacer(Modifier.height(12.dp))
            Text(result.predictedClass.replaceFirstChar { it.uppercase() },
                fontSize = 32.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            Text("Confidence", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            LinearProgressIndicator(progress = { result.confidence },
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
            Text("${(result.confidence * 100).toInt()}%", fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Text("Latency: ${result.latencyMs}ms", fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            Text("All scores", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            result.allScores.entries.sortedByDescending { it.value }.take(5).forEach { (label, score) ->
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(label, modifier = Modifier.width(80.dp), fontSize = 12.sp)
                    LinearProgressIndicator(progress = { score },
                        modifier = Modifier.weight(1f).height(6.dp),
                        trackColor = MaterialTheme.colorScheme.surfaceVariant)
                    Spacer(Modifier.width(8.dp))
                    Text("${(score * 100).toInt()}%", fontSize = 11.sp,
                        modifier = Modifier.width(36.dp), textAlign = TextAlign.End)
                }
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

@Composable
fun MetricsDashboard(metrics: SessionStats) {
    Card {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Analytics, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("Session Metrics", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
            Spacer(Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetricTile(Modifier.weight(1f), "Total", "${metrics.totalRequests}")
                MetricTile(Modifier.weight(1f), "Fast", "${metrics.fastModelRequests}")
                MetricTile(Modifier.weight(1f), "Accurate", "${metrics.accurateModelRequests}")
            }
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetricTile(Modifier.weight(1f), "Avg Fast", "${metrics.avgFastLatencyMs.toInt()}ms")
                MetricTile(Modifier.weight(1f), "Avg Accurate", "${metrics.avgAccurateLatencyMs.toInt()}ms")
                MetricTile(Modifier.weight(1f), "Avg Conf", "${(metrics.avgConfidence * 100).toInt()}%")
            }
            if (metrics.recentRecords.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text("Recent Decisions", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                metrics.recentRecords.takeLast(5).reversed().forEach { record ->
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(8.dp).background(
                            if (record.modelType == "FAST") Color(0xFF4CAF50) else Color(0xFF2196F3),
                            shape = RoundedCornerShape(50)))
                        Spacer(Modifier.width(8.dp))
                        Text(record.predictedClass, fontSize = 12.sp, modifier = Modifier.width(70.dp))
                        Text("${record.latencyMs}ms", fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(50.dp))
                        Text(record.modelType, fontSize = 11.sp,
                            color = if (record.modelType == "FAST") Color(0xFF4CAF50) else Color(0xFF2196F3))
                    }
                }
            }
        }
    }
}

@Composable
fun MetricTile(modifier: Modifier = Modifier, label: String, value: String) {
    Card(modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(modifier = Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}