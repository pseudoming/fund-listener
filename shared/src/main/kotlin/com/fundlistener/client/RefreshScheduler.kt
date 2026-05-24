package com.fundlistener.client

import com.fundlistener.repository.FundRepository
import com.fundlistener.service.ValuationService
import com.fundlistener.service.FundService
import kotlinx.coroutines.*
import org.slf4j.LoggerFactory

/**
 * 定时刷新调度器 — 交易时段每 30s 执行估值刷新，非交易时段休眠。
 *
 * JVM / Android 共用同一套逻辑，仅 CoroutineScope 来源不同：
 *   - JVM: Application 进程中直接启动
 *   - Android: Foreground Service 的 lifecycleScope 中启动
 */
class RefreshScheduler(
    private val valuationService: ValuationService,
    private val fundRepository: FundRepository,
    private val fundService: FundService
) {
    private val logger = LoggerFactory.getLogger(RefreshScheduler::class.java)

    private var job: Job? = null

    /**
     * 启动定时刷新循环。
     *
     * @param scope 协程作用域（JVM: GlobalScope / Android: serviceScope）
     */
    fun start(scope: CoroutineScope) {
        if (job?.isActive == true) {
            logger.info("RefreshScheduler: already running, skipping")
            return
        }
        job = scope.launch(Dispatchers.Default) {
            logger.info("RefreshScheduler: started")
            while (isActive) {
                try {
                    val isTrading = TradingHoursChecker.isTradingNow()
                    val isNavUpdating = TradingHoursChecker.isNavUpdatingTime()
                    if (isTrading || isNavUpdating) {
                        refreshAll()
                        if (isTrading) {
                            delay(30_000L) // 交易时段每 30s 刷新
                        } else {
                            delay(300_000L) // 净值更新时段每 5 分钟刷新
                        }
                    } else {
                        // 非交易且非净值更新时段：每 15 分钟检查一次
                        delay(900_000L)
                    }
                } catch (e: CancellationException) {
                    logger.info("RefreshScheduler: cancelled")
                    break
                } catch (e: Exception) {
                    logger.error("RefreshScheduler: unexpected error", e)
                    delay(10_000L)
                }
            }
        }
    }

    /** 停止定时刷新 */
    fun stop() {
        job?.cancel()
        job = null
        logger.info("RefreshScheduler: stopped")
    }

    private suspend fun refreshAll() {
        val positionCodes = fundRepository.getAllPositions().map { it.fundCode }
        val watchlistCodes = fundRepository.getAllWatchlist()
        val allCodes = (positionCodes + watchlistCodes).distinct()

        if (allCodes.isEmpty()) {
            logger.debug("RefreshScheduler: no funds to refresh")
            return
        }
        logger.info("RefreshScheduler: refreshing {} fund(s) (positions: {}, watchlist: {})", 
            allCodes.size, positionCodes.size, watchlistCodes.size)
        
        for (code in allCodes) {
            try {
                // 1. 刷新实时估值与元数据缓存（写入 fund_metadata & fund_nav_history）
                try {
                    fundService.getRealtimeEstimation(code)
                } catch (e: Exception) {
                    logger.warn("RefreshScheduler: failed to refresh estimation cache for {}: {}", code, e.message)
                }

                // 2. 刷新重仓穿透估值与历史百分位快照（写入 fund_valuation_snapshot）
                try {
                    valuationService.evaluate(code)
                } catch (e: Exception) {
                    logger.warn("RefreshScheduler: valuation failed for fund {}: {}", code, e.message)
                }
            } catch (e: Exception) {
                logger.error("RefreshScheduler: unexpected error for fund {}", code, e)
            }
        }
    }
}

