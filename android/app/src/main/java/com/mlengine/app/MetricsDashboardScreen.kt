package com.mlengine.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MetricsDashboardScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.refreshMetrics() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Live Metrics", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refreshMetrics() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        }
    ) { padding ->
        val metrics = uiState.metrics
        if (metrics == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── Summary cards ─────────────────────────────────────────────
            Text("Session Summary", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SummaryCard(Modifier.weight(1f), "Total Requests", "${metrics.totalRequests}", Color(0xFF6650A4))
                SummaryCard(Modifier.weight(1f), "Avg Confidence", "${(metrics.avgConfidence * 100).toInt()}%", Color(0xFF4CAF50))
            }

            // ── Model usage breakdown ──────────────────────────────────────
            Text("Model Usage", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Card {
                Column(Modifier.padding(16.dp)) {
                    val total = metrics.totalRequests.coerceAtLeast(1)
                    val fastPct = metrics.fastModelRequests.toFloat() / total
                    val accuratePct = metrics.accurateModelRequests.toFloat() / total

                    ModelUsageRow("FAST", metrics.fastModelRequests, fastPct, Color(0xFF4CAF50))
                    Spacer(Modifier.height(12.dp))
                    ModelUsageRow("ACCURATE", metrics.accurateModelRequests, accuratePct, Color(0xFF2196F3))
                }
            }

            // ── Latency comparison ─────────────────────────────────────────
            Text("Latency Comparison", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Card {
                Column(Modifier.padding(16.dp)) {
                    LatencyBar("Fast Model", metrics.avgFastLatencyMs, 5000.0, Color(0xFF4CAF50))
                    Spacer(Modifier.height(12.dp))
                    LatencyBar("Accurate Model", metrics.avgAccurateLatencyMs, 5000.0, Color(0xFF2196F3))
                }
            }

            // ── Routing timeline ───────────────────────────────────────────
            if (metrics.recentRecords.isNotEmpty()) {
                Text("Routing Timeline", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Card {
                    Column(Modifier.padding(16.dp)) {
                        // Visual timeline of model routing decisions
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            metrics.recentRecords.takeLast(20).forEach { record ->
                                val color = if (record.modelType == "FAST") Color(0xFF4CAF50)
                                            else Color(0xFF2196F3)
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(32.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(color),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = if (record.modelType == "FAST") "F" else "A",
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            LegendItem(Color(0xFF4CAF50), "Fast (F)")
                            LegendItem(Color(0xFF2196F3), "Accurate (A)")
                        }
                    }
                }

                // ── Detailed records ───────────────────────────────────────
                Text("Recent Decisions", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Card {
                    Column(Modifier.padding(16.dp)) {
                        metrics.recentRecords.reversed().forEach { record ->
                            RecordRow(record)
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        }
                    }
                }
            }

            // ── Adaptive routing explanation ───────────────────────────────
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("How Adaptive Routing Works",
                        fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "The router monitors rolling average latency. " +
                        "If the fast model's average latency stays below 70% of the " +
                        "latency budget (default: 150ms), the router upgrades to the " +
                        "accurate model for better predictions. This mirrors the " +
                        "ABR algorithm used in Netflix and YouTube streaming.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        lineHeight = 18.sp
                    )
                }
            }
        }
    }
}

@Composable
fun SummaryCard(modifier: Modifier, label: String, value: String, color: Color) {
    Card(modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f))) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value, fontWeight = FontWeight.Bold, fontSize = 28.sp, color = color)
            Text(label, fontSize = 12.sp, textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun ModelUsageRow(label: String, count: Int, fraction: Float, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(12.dp).background(color, CircleShape))
        Spacer(Modifier.width(8.dp))
        Text(label, modifier = Modifier.width(80.dp), fontSize = 13.sp, fontWeight = FontWeight.Medium)
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier.weight(1f).height(8.dp),
            color = color,
            trackColor = color.copy(alpha = 0.2f)
        )
        Spacer(Modifier.width(8.dp))
        Text("$count", fontSize = 13.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.width(30.dp), textAlign = TextAlign.End)
    }
}

@Composable
fun LatencyBar(label: String, latencyMs: Double, maxMs: Double, color: Color) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, fontSize = 13.sp)
            Text("${latencyMs.toInt()}ms", fontSize = 13.sp,
                fontWeight = FontWeight.Bold, color = color)
        }
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { (latencyMs / maxMs).toFloat().coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(10.dp),
            color = color,
            trackColor = color.copy(alpha = 0.2f)
        )
    }
}

@Composable
fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).background(color, RoundedCornerShape(2.dp)))
        Spacer(Modifier.width(4.dp))
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun RecordRow(record: InferenceRecord) {
    val color = if (record.modelType == "FAST") Color(0xFF4CAF50) else Color(0xFF2196F3)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(8.dp).background(color, CircleShape))
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(record.predictedClass.replaceFirstChar { it.uppercase() },
                fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Text(record.routingReason, fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("${(record.confidence * 100).toInt()}%",
                fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text("${record.latencyMs}ms", fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
