package com.fundlistener.client

import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class QuoteClientTest {

    private val client = QuoteClient(
        io.ktor.client.HttpClient(io.ktor.client.engine.okhttp.OkHttp)
    )

    @Test
    fun `formatTencentCode should prepend sh or sz correctly`() {
        // Stock codes
        assertEquals("sh600519", client.formatTencentCode("600519", "STOCK"))
        assertEquals("sz000858", client.formatTencentCode("000858", "STOCK"))
        assertEquals("sz300059", client.formatTencentCode("300059", "STOCK"))
        // Index codes
        assertEquals("sh000300", client.formatTencentCode("000300", "INDEX"))
        assertEquals("sz399001", client.formatTencentCode("399001", "INDEX"))
        // Codes with existing prefixes
        assertEquals("sh600519", client.formatTencentCode("sh600519", "STOCK"))
        assertEquals("sz000858", client.formatTencentCode("sz000858", "STOCK"))
    }

    @Test
    fun `parseQuotes should extract details from valid Tencent response`() {
        val raw = """
            v_sz000858="51~五 粮 液~000858~179.80~1~2~3~4~5~6~7~8~9~10~11~12~13~14~15~16~17~18~19~20~21~22~23~24~25~26~27~0.74~32";
            v_sh000300="51~沪深300~000300~3950.12~1~2~3~4~5~6~7~8~9~10~11~12~13~14~15~16~17~18~19~20~21~22~23~24~25~26~27~-0.72~32";
        """.trimIndent()

        val quotes = client.parseQuotes(raw)

        val stockQuote = quotes["000858"]
        assertNotNull(stockQuote)
        assertEquals("五 粮 液", stockQuote.name)
        assertEquals(BigDecimal("179.80"), stockQuote.price)
        assertEquals(BigDecimal("0.74"), stockQuote.changePercent)

        val indexQuote = quotes["000300"]
        assertNotNull(indexQuote)
        assertEquals("沪深300", indexQuote.name)
        assertEquals(BigDecimal("3950.12"), indexQuote.price)
        assertEquals(BigDecimal("-0.72"), indexQuote.changePercent)
    }
}
