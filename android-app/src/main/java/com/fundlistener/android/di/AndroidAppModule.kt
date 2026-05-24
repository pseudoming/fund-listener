package com.fundlistener.android.di

import com.fundlistener.android.data.DataMigration
import com.fundlistener.android.data.FundDatabase
import com.fundlistener.android.data.RoomFundRepository
import com.fundlistener.client.QuoteCache
import com.fundlistener.client.StockQuoteClient
import com.fundlistener.client.TianTianFundClient
import com.fundlistener.repository.FundRepository
import com.fundlistener.service.*
import com.fundlistener.service.ocr.OcrService
import com.fundlistener.android.ocr.MlKitOcrService
import androidx.room.Room
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module
import java.util.concurrent.TimeUnit

/**
 * Android DI 模块 — 注入 Room 实现，替代 JVM 的 SQLite。
 *
 * 与 shared 模块的 appModule 保持一致的结构，
 * 仅将 FundRepository 替换为 RoomFundRepository，
 * OCR 替换为 MockOcrService（Phase 6.4 替换为 ML Kit）。
 */
val androidAppModule = module {

    // HTTP Client
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

    // Room Database
    single {
        Room.databaseBuilder(
            androidContext(),
            FundDatabase::class.java,
            "fund_listener.db"  // Room DB name, distinct from old "fund-listener.db"
        ).build()
    }

    // Repository — Room 实现
    single<FundRepository> {
        val db: FundDatabase = get()
        RoomFundRepository(db)
    }

    // Data Migration
    single {
        DataMigration(androidContext(), get())
    }

    // Data clients
    single { TianTianFundClient(get()) }
    single { StockQuoteClient(get()) }

    // Quote cache
    single { QuoteCache(get()) }

    // Services
    single { FundService(get()) }
    single { PositionService(get()) }
    single { ValuationService(get(), get(), get(), get()) }
    single { DashboardService(get(), get()) }

    // Event Bus
    single<ValuationEventBus> { LogOnlyValuationEventBus() }

    // OCR — ML Kit offline (replaces MockOcrService from dev phase)
    single<OcrService> { MlKitOcrService() }
}
