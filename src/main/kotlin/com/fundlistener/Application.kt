package com.fundlistener

import com.fundlistener.plugins.configureKoin
import com.fundlistener.plugins.configureRouting
import com.fundlistener.plugins.configureSerialization
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*

fun main() {
    embeddedServer(
        Netty,
        port = 8080,
        host = "0.0.0.0",
        module = Application::module
    ).start(wait = true)
}

fun Application.module() {
    configureKoin()
    configureSerialization()
    configureRouting()
}
