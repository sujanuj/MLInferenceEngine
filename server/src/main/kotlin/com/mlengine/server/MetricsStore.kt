package com.mlengine.server

import java.util.concurrent.CopyOnWriteArrayList

class MetricsStore {

    data class InferenceRecord(
        val timestamp: Long = System.currentTimeMillis(),
        val modelType: String,
        val predictedClass: String,
        val confidence: Float,
        val latencyMs: Long,
        val routingReason: String
    )

    data class SessionStats(
        val totalRequests: Int,
        val fastModelRequests: Int,
        val accurateModelRequests: Int,
        val avgFastLatencyMs: Double,
        val avgAccurateLatencyMs: Double,
        val avgConfidence: Double,
        val recentRecords: List<InferenceRecord>
    )

    private val records = CopyOnWriteArrayList<InferenceRecord>()
    private val maxRecords = 500

    fun record(
        result: InferenceEngine.InferenceResult,
        routingReason: String
    ) {
        val rec = InferenceRecord(
            modelType      = result.modelType,
            predictedClass = result.predictedClass,
            confidence     = result.confidence,
            latencyMs      = result.latencyMs,
            routingReason  = routingReason
        )
        records.add(rec)
        while (records.size > maxRecords) records.removeAt(0)
    }

    fun getStats(): SessionStats {
        val all      = records.toList()
        val fast     = all.filter { it.modelType == "FAST" }
        val accurate = all.filter { it.modelType == "ACCURATE" }
        return SessionStats(
            totalRequests         = all.size,
            fastModelRequests     = fast.size,
            accurateModelRequests = accurate.size,
            avgFastLatencyMs      = fast.map { it.latencyMs }.average().takeIf { !it.isNaN() } ?: 0.0,
            avgAccurateLatencyMs  = accurate.map { it.latencyMs }.average().takeIf { !it.isNaN() } ?: 0.0,
            avgConfidence         = all.map { it.confidence.toDouble() }.average().takeIf { !it.isNaN() } ?: 0.0,
            recentRecords         = all.takeLast(20)
        )
    }

    fun getAllRecords(): List<InferenceRecord> = records.toList()
}
