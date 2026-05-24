package com.fundlistener.client

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class TianTianFundClientTest {

    private val client = TianTianFundClient(
        io.ktor.client.HttpClient(io.ktor.client.engine.okhttp.OkHttp)
    )

    @Test
    fun `parseJsonp should extract fund data from valid JSONP`() {
        val jsonp = """jsonpgz({"fundcode":"110022","name":"易方达消费行业股票","jzrq":"2026-05-14","dwjz":"3.0160","gsz":"2.9830","gszzl":"-1.10","gztime":"2026-05-15 15:00"});"""
        val result = client.parseJsonp(jsonp)

        assertNotNull(result)
        assertEquals("110022", result.fundcode)
        assertEquals("易方达消费行业股票", result.name)
        assertEquals("2026-05-14", result.jzrq)
        assertEquals("3.0160", result.dwjz)
        assertEquals("2.9830", result.gsz)
        assertEquals("-1.10", result.gszzl)
        assertEquals("2026-05-15 15:00", result.gztime)
    }

    @Test
    fun `parseJsonp should return null for invalid JSONP format`() {
        val invalid = """{"fundcode":"110022"}"""
        val result = client.parseJsonp(invalid)
        assertNull(result)
    }

    @Test
    fun `parseJsonp should handle JSONP without trailing semicolon`() {
        val jsonp = """jsonpgz({"fundcode":"110022","name":"测试基金","jzrq":"2026-01-01","dwjz":"1.0000","gsz":"1.0100","gszzl":"1.00","gztime":"2026-01-01 15:00"})"""
        val result = client.parseJsonp(jsonp)
        assertNotNull(result)
        assertEquals("110022", result.fundcode)
    }

    @Test
    fun `toEstimation should map raw fields correctly`() {
        val raw = com.fundlistener.model.TianTianFundRaw(
            fundcode = "110022",
            name = "易方达消费行业股票",
            jzrq = "2026-05-14",
            dwjz = "3.0160",
            gsz = "2.9830",
            gszzl = "-1.10",
            gztime = "2026-05-15 15:00"
        )
        val estimation = raw.toEstimation()

        assertEquals("110022", estimation.code)
        assertEquals("易方达消费行业股票", estimation.name)
        assertEquals("2026-05-14", estimation.navDate)
        assertEquals("3.0160", estimation.nav)
        assertEquals("2.9830", estimation.estimatedNav)
        assertEquals("-1.10", estimation.estimatedGrowthRate)
        assertEquals("2026-05-15 15:00", estimation.estimationTime)
        assertEquals("", estimation.yesterdayNav)
    }
}
