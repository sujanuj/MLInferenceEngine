package com.mlengine.server

import com.google.gson.Gson
import com.google.gson.JsonObject
import java.io.File
import java.util.concurrent.TimeUnit

class InferenceEngine {

    private val gson = Gson()
    private val pythonScript = resolvePythonScript()

    enum class ModelType { FAST, ACCURATE }

    data class InferenceResult(
        val modelType: String,
        val predictedClass: String,
        val confidence: Float,
        val latencyMs: Long,
        val allScores: Map<String, Float>
    )

    fun runInference(imageBytes: ByteArray, modelType: ModelType): InferenceResult {
        val tempFile = File.createTempFile("mlengine_input_", ".jpg")
        try {
            tempFile.writeBytes(imageBytes)
            val modelFlag = when (modelType) {
                ModelType.FAST     -> "fast"
                ModelType.ACCURATE -> "accurate"
            }
            val start = System.currentTimeMillis()
            val process = ProcessBuilder(
                "python3", pythonScript.absolutePath,
                "--image", tempFile.absolutePath,
                "--model", modelFlag
            )
                .redirectErrorStream(false)
                .start()

            val finished = process.waitFor(30, TimeUnit.SECONDS)
            val latencyMs = System.currentTimeMillis() - start

            if (!finished) {
                process.destroyForcibly()
                throw RuntimeException("Inference timed out after 30s")
            }

            // Only read stdout — stderr is separate now
            val output = process.inputStream.bufferedReader().readText().trim()
            val errorOutput = process.errorStream.bufferedReader().readText().trim()

            if (process.exitValue() != 0) {
                throw RuntimeException("Inference failed: $output $errorOutput")
            }

            // Find the JSON line — it starts with {
            val jsonLine = output.lines().lastOrNull { it.trim().startsWith("{") }
                ?: throw RuntimeException("No JSON in output: $output")

            val json = gson.fromJson(jsonLine, JsonObject::class.java)
            val predictedClass = json.get("predicted_class").asString
            val confidence = json.get("confidence").asFloat

            // Parse allScores from JsonObject directly
            val allScoresJson = json.getAsJsonObject("all_scores")
            val allScores = allScoresJson.entrySet().associate { (key, value) ->
                key to value.asFloat
            }

            return InferenceResult(
                modelType      = modelType.name,
                predictedClass = predictedClass,
                confidence     = confidence,
                latencyMs      = latencyMs,
                allScores      = allScores
            )
        } finally {
            tempFile.delete()
        }
    }

    private fun resolvePythonScript(): File {
        val base = System.getProperty("user.dir")
        val candidates = listOf(
            File("$base/../models/inference/run_inference.py"),
            File("$base/../../models/inference/run_inference.py"),
            File("/Users/sujan/Developer/MLInferenceEngine/models/inference/run_inference.py")
        )
        return candidates.firstOrNull { it.exists() }
            ?: File("/Users/sujan/Developer/MLInferenceEngine/models/inference/run_inference.py")
    }
}
