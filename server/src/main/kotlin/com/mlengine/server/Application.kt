package com.mlengine.server

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.serialization.gson.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.http.*

fun main() {
    embeddedServer(Netty, port = 8080, host = "0.0.0.0") {
        install(ContentNegotiation) { gson { setPrettyPrinting() } }
        install(CORS) {
            allowMethod(HttpMethod.Get)
            allowMethod(HttpMethod.Post)
            allowHeader(HttpHeaders.ContentType)
            anyHost()
        }
        val inferenceEngine = InferenceEngine()
        val metricsStore = MetricsStore()
        configureRoutes(inferenceEngine, metricsStore)
    }.start(wait = true)
}
