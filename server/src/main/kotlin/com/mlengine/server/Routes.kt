package com.mlengine.server

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.delay

// Global network simulation state
object NetworkSimulator {
    enum class NetworkQuality(val extraDelayMs: Long, val label: String) {
        GOOD(0L, "Good — no delay"),
        POOR(500L, "Poor — +500ms delay"),
        TERRIBLE(2000L, "Terrible — +2000ms delay")
    }

    var currentQuality = NetworkQuality.GOOD

    suspend fun applyDelay() {
        if (currentQuality.extraDelayMs > 0) {
            delay(currentQuality.extraDelayMs)
        }
    }
}

fun Application.configureRoutes(
    inferenceEngine: InferenceEngine,
    metricsStore: MetricsStore
) {
    routing {

        get("/health") {
            call.respond(mapOf(
                "status" to "ok",
                "server" to "MLInferenceEngine",
                "networkQuality" to NetworkSimulator.currentQuality.label
            ))
        }

        // ── Network simulation control ─────────────────────────────────────
        post("/simulate/network/{quality}") {
            val quality = call.parameters["quality"]?.uppercase() ?: "GOOD"
            val newQuality = try {
                NetworkSimulator.NetworkQuality.valueOf(quality)
            } catch (e: Exception) {
                return@post call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "Invalid quality. Use: GOOD, POOR, TERRIBLE")
                )
            }
            NetworkSimulator.currentQuality = newQuality
            call.respond(mapOf(
                "networkQuality" to newQuality.name,
                "extraDelayMs" to newQuality.extraDelayMs,
                "label" to newQuality.label
            ))
        }

        get("/simulate/network") {
            call.respond(mapOf(
                "currentQuality" to NetworkSimulator.currentQuality.name,
                "extraDelayMs" to NetworkSimulator.currentQuality.extraDelayMs,
                "label" to NetworkSimulator.currentQuality.label
            ))
        }

        // ── Single model inference ─────────────────────────────────────────
        post("/infer/{model}") {
            val modelParam = call.parameters["model"] ?: "fast"
            val modelType = when (modelParam.lowercase()) {
                "accurate" -> InferenceEngine.ModelType.ACCURATE
                else       -> InferenceEngine.ModelType.FAST
            }
            val imageBytes = receiveImageBytes(call)
                ?: return@post call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "No image provided")
                )
            try {
                NetworkSimulator.applyDelay()
                val result = inferenceEngine.runInference(imageBytes, modelType)
                metricsStore.record(result, routingReason = "explicit_${modelParam}")
                call.respond(result)
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError,
                    mapOf("error" to (e.message ?: "Inference failed")))
            }
        }

        // ── Adaptive inference ─────────────────────────────────────────────
        post("/infer/adaptive") {
            val latencyBudget = call.request.queryParameters["latencyBudgetMs"]
                ?.toLongOrNull() ?: 150L
            val imageBytes = receiveImageBytes(call)
                ?: return@post call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "No image provided")
                )
            val stats = metricsStore.getStats()
            val useAccurate = when {
                stats.totalRequests == 0 -> false
                stats.avgFastLatencyMs < latencyBudget * 0.7 -> true
                else -> false
            }
            val modelType = if (useAccurate) InferenceEngine.ModelType.ACCURATE
                            else             InferenceEngine.ModelType.FAST
            val routingReason = if (useAccurate)
                "adaptive_accurate: avg=${stats.avgFastLatencyMs.toInt()}ms < budget*0.7=${(latencyBudget*0.7).toInt()}ms"
            else
                "adaptive_fast: avg=${stats.avgFastLatencyMs.toInt()}ms >= budget*0.7=${(latencyBudget*0.7).toInt()}ms"

            try {
                NetworkSimulator.applyDelay()
                val result = inferenceEngine.runInference(imageBytes, modelType)
                metricsStore.record(result, routingReason)
                call.respond(mapOf(
                    "result"          to result,
                    "routingReason"   to routingReason,
                    "latencyBudgetMs" to latencyBudget,
                    "networkQuality"  to NetworkSimulator.currentQuality.name,
                    "extraDelayMs"    to NetworkSimulator.currentQuality.extraDelayMs
                ))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError,
                    mapOf("error" to (e.message ?: "Inference failed")))
            }
        }

        get("/metrics") { call.respond(metricsStore.getStats()) }
        get("/metrics/history") { call.respond(mapOf("records" to metricsStore.getAllRecords())) }
    }
}

private suspend fun receiveImageBytes(call: ApplicationCall): ByteArray? {
    val contentType = call.request.contentType()
    return when {
        contentType.match(ContentType.MultiPart.FormData) -> {
            val multipart = call.receiveMultipart()
            var bytes: ByteArray? = null
            multipart.forEachPart { part ->
                if (part is PartData.FileItem && part.name == "image") {
                    bytes = part.streamProvider().readBytes()
                }
                part.dispose()
            }
            bytes
        }
        contentType.match(ContentType.Image.JPEG) ||
        contentType.match(ContentType.Image.PNG) -> call.receive<ByteArray>()
        else -> null
    }
}
