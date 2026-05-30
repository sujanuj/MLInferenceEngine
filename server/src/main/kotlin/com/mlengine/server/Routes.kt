package com.mlengine.server

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Application.configureRoutes(
    inferenceEngine: InferenceEngine,
    metricsStore: MetricsStore
) {
    routing {

        get("/health") {
            call.respond(mapOf("status" to "ok", "server" to "MLInferenceEngine"))
        }

        post("/infer/{model}") {
            val modelParam = call.parameters["model"] ?: "fast"
            val modelType = when (modelParam.lowercase()) {
                "accurate" -> InferenceEngine.ModelType.ACCURATE
                else       -> InferenceEngine.ModelType.FAST
            }
            val imageBytes = receiveImageBytes(call)
                ?: return@post call.respond(
                    HttpStatusCode.BadRequest,
                    mapOf("error" to "No image provided. Send multipart/form-data with field 'image'")
                )
            try {
                val result = inferenceEngine.runInference(imageBytes, modelType)
                metricsStore.record(result, routingReason = "explicit_${modelParam}")
                call.respond(result)
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to (e.message ?: "Inference failed"))
                )
            }
        }

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
                "adaptive_accurate: avg_fast=${stats.avgFastLatencyMs.toInt()}ms < budget*0.7=${(latencyBudget*0.7).toInt()}ms"
            else
                "adaptive_fast: budget=${latencyBudget}ms"
            try {
                val result = inferenceEngine.runInference(imageBytes, modelType)
                metricsStore.record(result, routingReason)
                call.respond(
                    mapOf(
                        "result"          to result,
                        "routingReason"   to routingReason,
                        "latencyBudgetMs" to latencyBudget
                    )
                )
            } catch (e: Exception) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    mapOf("error" to (e.message ?: "Inference failed"))
                )
            }
        }

        get("/metrics") {
            call.respond(metricsStore.getStats())
        }

        get("/metrics/history") {
            call.respond(mapOf("records" to metricsStore.getAllRecords()))
        }
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
        contentType.match(ContentType.Image.PNG) -> {
            call.receive<ByteArray>()
        }
        else -> null
    }
}
