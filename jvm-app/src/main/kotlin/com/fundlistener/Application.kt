package com.fundlistener

import com.fundlistener.client.RefreshScheduler
import com.fundlistener.plugins.configureKoin
import com.fundlistener.plugins.configureRouting
import com.fundlistener.plugins.configureSerialization
import com.fundlistener.repository.FundRepository
import com.fundlistener.service.ValuationService
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import kotlinx.coroutines.*
import org.koin.ktor.ext.inject

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

    val fundService by inject<com.fundlistener.service.FundService>()
    val logger = org.slf4j.LoggerFactory.getLogger("com.fundlistener.Application")

    // Start local fund codes sync
    launch(Dispatchers.IO) {
        try {
            logger.info("Initializing fund directory cache sync...")
            fundService.syncFundsIfEmpty()
            logger.info("Fund directory cache sync completed.")
        } catch (e: Exception) {
            logger.error("Failed to sync fund codes directory on start", e)
        }
    }

    // Start periodic refresh (trading hours: 30s, non-trading: sleep)
    launch(Dispatchers.Default) {
        val scheduler = RefreshScheduler(
            valuationService = inject<ValuationService>().value,
            fundRepository = inject<FundRepository>().value,
            fundService = inject<com.fundlistener.service.FundService>().value
        )
        scheduler.start(this)
    }
}

