package com.mlengine.app

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

data class InferenceResult(
    @SerializedName("modelType")     val modelType: String,
    @SerializedName("predictedClass") val predictedClass: String,
    @SerializedName("confidence")    val confidence: Float,
    @SerializedName("latencyMs")     val latencyMs: Long,
    @SerializedName("allScores")     val allScores: Map<String, Float>
)

data class AdaptiveResult(
    @SerializedName("result")          val result: InferenceResult,
    @SerializedName("routingReason")   val routingReason: String,
    @SerializedName("latencyBudgetMs") val latencyBudgetMs: Long
)

data class InferenceRecord(
    @SerializedName("timestamp")      val timestamp: Long,
    @SerializedName("modelType")      val modelType: String,
    @SerializedName("predictedClass") val predictedClass: String,
    @SerializedName("confidence")     val confidence: Float,
    @SerializedName("latencyMs")      val latencyMs: Long,
    @SerializedName("routingReason")  val routingReason: String
)

data class SessionStats(
    @SerializedName("totalRequests")         val totalRequests: Int,
    @SerializedName("fastModelRequests")     val fastModelRequests: Int,
    @SerializedName("accurateModelRequests") val accurateModelRequests: Int,
    @SerializedName("avgFastLatencyMs")      val avgFastLatencyMs: Double,
    @SerializedName("avgAccurateLatencyMs")  val avgAccurateLatencyMs: Double,
    @SerializedName("avgConfidence")         val avgConfidence: Double,
    @SerializedName("recentRecords")         val recentRecords: List<InferenceRecord>
)

class ApiClient(private val baseUrl: String = "http://10.0.2.2:8080") {

    private val client = OkHttpClient()
    private val gson = Gson()

    sealed class Result<T> {
        data class Success<T>(val data: T) : Result<T>()
        data class Error<T>(val message: String) : Result<T>()
    }

    fun infer(
        imageBytes: ByteArray,
        mode: String = "adaptive",
        latencyBudgetMs: Long = 150
    ): Result<InferenceResult> {
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart(
                "image", "image.jpg",
                imageBytes.toRequestBody("image/jpeg".toMediaType())
            )
            .build()

        val url = when (mode) {
            "adaptive" -> "$baseUrl/infer/adaptive?latencyBudgetMs=$latencyBudgetMs"
            else       -> "$baseUrl/infer/$mode"
        }

        val request = Request.Builder().url(url).post(body).build()

        return try {
            val response = client.newCall(request).execute()
            val json = response.body?.string() ?: return Result.Error("Empty response")
            if (!response.isSuccessful) return Result.Error("Server error: ${response.code}")

            val result = if (mode == "adaptive") {
                gson.fromJson(json, AdaptiveResult::class.java).result
            } else {
                gson.fromJson(json, InferenceResult::class.java)
            }
            Result.Success(result)
        } catch (e: IOException) {
            Result.Error("Network error: ${e.message}")
        }
    }

    fun getMetrics(): Result<SessionStats> {
        val request = Request.Builder().url("$baseUrl/metrics").get().build()
        return try {
            val response = client.newCall(request).execute()
            val json = response.body?.string() ?: return Result.Error("Empty response")
            Result.Success(gson.fromJson(json, SessionStats::class.java))
        } catch (e: IOException) {
            Result.Error("Network error: ${e.message}")
        }
    }

    fun checkHealth(): Boolean {
        return try {
            val request = Request.Builder().url("$baseUrl/health").get().build()
            client.newCall(request).execute().isSuccessful
        } catch (e: Exception) {
            false
        }
    }
}
