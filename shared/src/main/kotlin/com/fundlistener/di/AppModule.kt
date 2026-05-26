package com.fundlistener.di

import com.fundlistener.client.QuoteCache
import com.fundlistener.client.QuoteClient
import com.fundlistener.client.StockQuoteClient
import com.fundlistener.client.TianTianFundClient
import com.fundlistener.service.CustomValuationEngine
import com.fundlistener.service.FundService
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import org.koin.dsl.module
import java.util.concurrent.TimeUnit

val appModule = module {
    // HTTP Client — OkHttp engine, 用于所有外部数据抓取
    single<HttpClient> {
        HttpClient(OkHttp) {
            engine {
                config {
                    connectTimeout(10, TimeUnit.SECONDS)
                    readTimeout(10, TimeUnit.SECONDS)
                    writeTimeout(10, TimeUnit.SECONDS)
                }
            }
        }
    }

    // Database
    single { 
        com.fundlistener.db.DatabaseFactory().apply { 
            init() 
        } 
    }
    single<com.fundlistener.repository.FundRepository> { 
        com.fundlistener.repository.SqliteFundRepository(get()) 
    }

    // Data clients
    single { TianTianFundClient(get()) }
    single { StockQuoteClient(get()) }
    single { QuoteClient(get()) }

    // Quote cache — wraps StockQuoteClient with TTL-based caching and QuoteClient fallback
    single { QuoteCache(get(), get(), get()) }

    // Services
    single { CustomValuationEngine(get(), get(), get(), get()) }
    single { FundService(get(), get(), get(), get()) }
    single { com.fundlistener.service.PositionService(get()) }
    single { com.fundlistener.service.ValuationService(get(), get(), get(), get()) }
    single { com.fundlistener.service.DashboardService(get(), get(), get(), get()) }
    single { com.fundlistener.service.ValuationDisplayNormalizer(get()) }

    // Event Bus — LogOnly 实现，Phase 8 替换为 Notification 实现
    single<com.fundlistener.service.ValuationEventBus> {
        com.fundlistener.service.LogOnlyValuationEventBus()
    }

    // OCR — 本地 OCR 实现 (RapidOCR + ONNX)
    single<com.fundlistener.service.ocr.OcrService> {
        com.fundlistener.service.ocr.LocalOcrService(repository = get(), tianTianFundClient = get())
    }
}
