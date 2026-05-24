package com.fundlistener.client

import kotlinx.coroutines.delay
import org.slf4j.LoggerFactory
import kotlin.math.min
import kotlin.math.pow

/**
 * 指数退避重试策略 — 外部 API 调用失败时自动重试。
 *
 * 支持三种模式：
 *   - STANDARD: 最多重试 [maxRetries] 次，baseDelay 指数增长
 *   - PERSISTENT: 持续重试直到 maxTotalElapsed，适合非交易时段的低优先级请求
 *   - NONE: 不重试
 *
 * 使用方式：
 *   RetryPolicy.STANDARD.execute { client.fetchSomething() }
 */
object RetryPolicy {

    private val logger = LoggerFactory.getLogger(RetryPolicy::class.java)

    /** 标准重试：3 次，初始 1s，最大 8s */
    val STANDARD = Builder()
        .maxRetries(3)
        .baseDelayMillis(1000L)
        .maxDelayMillis(8000L)
        .build()

    /** 持久重试：用于非交易时段或非关键请求，最多累计 30s */
    val PERSISTENT = Builder()
        .maxRetries(5)
        .baseDelayMillis(2000L)
        .maxDelayMillis(15000L)
        .build()

    /** 不重试 */
    val NONE = Builder().maxRetries(0).build()

    class Builder {
        private var maxRetries: Int = 3
        private var baseDelayMillis: Long = 1000L
        private var maxDelayMillis: Long = 8000L
        private var retryableExceptions: Set<Class<out Throwable>> = setOf(
            java.io.IOException::class.java,
            java.net.SocketTimeoutException::class.java,
            java.util.concurrent.TimeoutException::class.java
        )

        fun maxRetries(n: Int) = apply { maxRetries = n }
        fun baseDelayMillis(ms: Long) = apply { baseDelayMillis = ms }
        fun maxDelayMillis(ms: Long) = apply { maxDelayMillis = ms }

        fun build() = RetryConfig(maxRetries, baseDelayMillis, maxDelayMillis, retryableExceptions)
    }

    data class RetryConfig(
        val maxRetries: Int,
        val baseDelayMillis: Long,
        val maxDelayMillis: Long,
        val retryableExceptions: Set<Class<out Throwable>>
    ) {
        /**
         * 执行带重试的 suspend 操作。
         *
         * @param block 要执行的挂起操作
         * @return 成功时返回结果，所有重试耗尽后返回 null
         */
        suspend fun <T> execute(block: suspend () -> T): T? {
            var attempt = 0
            var lastException: Throwable? = null

            while (attempt <= maxRetries) {
                try {
                    return block()
                } catch (e: Exception) {
                    lastException = e
                    if (!isRetryable(e) || attempt >= maxRetries) {
                        logger.warn("RetryPolicy: non-retryable or max retries reached", e)
                        return null
                    }
                    attempt++
                    val delayMs = min(baseDelayMillis * 2.0.pow(attempt - 1).toLong(), maxDelayMillis)
                    logger.info("RetryPolicy: retry {}/{} after {}ms — {}",
                        attempt, maxRetries, delayMs, e.message?.take(80))
                    delay(delayMs)
                }
            }
            return null
        }

        private fun isRetryable(e: Throwable): Boolean {
            return retryableExceptions.any { it.isInstance(e) } ||
                   e.cause?.let { retryableExceptions.any { cls -> cls.isInstance(it) } } == true
        }
    }
}
