package com.videoget.app.instagram

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Toast

class InstagramLoginActivity : Activity() {
    private lateinit var webView: WebView
    private val handler = Handler(Looper.getMainLooper())
    private val loginCheck = object : Runnable {
        override fun run() {
            if (!isFinishing && InstagramSession.isLoggedIn()) {
                completeLogin()
            } else if (!isFinishing) {
                handler.postDelayed(this, 500)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "连接 Instagram"
        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
            InstagramSession.saveUserAgent(this@InstagramLoginActivity, settings.userAgentString)
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    val host = request.url.host.orEmpty().lowercase()
                    return !(host == "instagram.com" || host.endsWith(".instagram.com") ||
                        host == "facebook.com" || host.endsWith(".facebook.com") ||
                        host == "fb.com" || host.endsWith(".fb.com"))
                }

                override fun onPageFinished(view: WebView, url: String) {
                    super.onPageFinished(view, url)
                    if (InstagramSession.isLoggedIn()) completeLogin()
                }
            }
            loadUrl("https://www.instagram.com/accounts/login/")
        }
        val done = Button(this).apply {
            text = "完成登录"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.BLACK)
            setOnClickListener {
                CookieManager.getInstance().flush()
                if (InstagramSession.isLoggedIn()) {
                    completeLogin()
                } else {
                    Toast.makeText(context, "尚未检测到 Instagram 登录状态", Toast.LENGTH_SHORT).show()
                }
            }
        }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(done, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)))
            addView(webView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        })
    }

    override fun onResume() {
        super.onResume()
        handler.removeCallbacks(loginCheck)
        handler.post(loginCheck)
    }

    override fun onPause() {
        handler.removeCallbacks(loginCheck)
        super.onPause()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }

    override fun onDestroy() {
        handler.removeCallbacks(loginCheck)
        webView.stopLoading()
        webView.destroy()
        super.onDestroy()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun completeLogin() {
        if (isFinishing) return
        CookieManager.getInstance().flush()
        setResult(RESULT_OK)
        finish()
    }
}
