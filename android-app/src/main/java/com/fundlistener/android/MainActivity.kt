package com.fundlistener.android

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity

/**
 * WebView 壳 — 加载 Ktor 嵌入式服务器提供的 Vue 3 前端。
 *
 * 关键配置（S17 要求）：
 *   - setMixedContentMode: MIXED_CONTENT_ALWAYS_ALLOW（local HTTP 不涉及混合内容问题）
 *   - setDomStorageEnabled: true（Vue 3 / Vant 需要 localStorage）
 *   - cleartext traffic: network_security_config.xml 已放行 localhost
 */
class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        webView = WebView(this)
        setContentView(webView)

        configureWebView()

        // Start Foreground Service to run Ktor
        startFundServer()

        // Load the Vue 3 frontend from local Ktor server
        webView.loadUrl("http://localhost:8080/")
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true           // Vue 3 + Vant 需要
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            allowFileAccess = false
            cacheMode = WebSettings.LOAD_DEFAULT
            setSupportZoom(false)
            builtInZoomControls = false
            useWideViewPort = true
            loadWithOverviewMode = true
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onReceivedError(
                view: WebView?,
                errorCode: Int,
                description: String?,
                failingUrl: String?
            ) {
                // Retry after 2s when Ktor is still starting up
                view?.postDelayed({
                    view.loadUrl("http://localhost:8080/")
                }, 2000)
            }
        }
    }

    private fun startFundServer() {
        val intent = android.content.Intent(this, FundServerService::class.java)
        startForegroundService(intent)
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }
}
