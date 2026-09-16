package com.videoget.app.downloads

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.fasterxml.jackson.databind.ObjectMapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.net.URI
import java.io.ByteArrayInputStream
import java.util.concurrent.atomic.AtomicBoolean

object ThreadsWebViewAnalyzer {
    private const val TIMEOUT_MS = 30_000L
    private const val POLL_MS = 800L
    private val mapper = ObjectMapper()

    @SuppressLint("SetJavaScriptEnabled")
    suspend fun analyze(context: Context, url: String): AnalyzedMedia = withContext(Dispatchers.Main) {
        require(isThreadsUrl(url)) { "不支持的 Threads 域名" }
        suspendCancellableCoroutine { continuation ->
            val finished = AtomicBoolean(false)
            val handler = Handler(Looper.getMainLooper())
            val webView = WebView(context.applicationContext)

            fun complete(result: Result<AnalyzedMedia>) {
                if (!finished.compareAndSet(false, true)) return
                handler.removeCallbacksAndMessages(null)
                webView.stopLoading()
                webView.loadUrl("about:blank")
                webView.removeAllViews()
                webView.destroy()
                if (continuation.isActive) continuation.resumeWith(result)
            }

            val timeout = Runnable {
                complete(Result.failure(IllegalStateException(
                    "Threads 动态页面加载超时，未能确认目标视频；请检查网络后重试",
                )))
            }

            fun poll() {
                if (finished.get()) return
                webView.evaluateJavascript(EXTRACT_SCRIPT) { raw ->
                    if (finished.get()) return@evaluateJavascript
                    val media = runCatching { parseExtraction(raw) }.getOrNull()
                    if (media != null) {
                        complete(Result.success(media))
                    } else {
                        handler.postDelayed(::poll, POLL_MS)
                    }
                }
            }

            webView.settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                allowFileAccess = false
                allowContentAccess = false
                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                setSupportMultipleWindows(false)
                javaScriptCanOpenWindowsAutomatically = false
                mediaPlaybackRequiresUserGesture = false
                userAgentString = userAgentString + " VideoGet/0.1"
            }
            webView.webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                    request.isForMainFrame && !isThreadsUrl(request.url.toString())

                override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                    if (request.url.scheme !in setOf("https", "data", "blob")) {
                        return WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(ByteArray(0)))
                    }
                    return null
                }

                override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                    if (!isThreadsUrl(url)) {
                        complete(Result.failure(SecurityException("Threads 页面跳转到了不受信任的地址")))
                    }
                }

                override fun onPageFinished(view: WebView, url: String) {
                    if (isThreadsUrl(url)) poll()
                }

                override fun onReceivedError(
                    view: WebView,
                    request: WebResourceRequest,
                    error: android.webkit.WebResourceError,
                ) {
                    if (request.isForMainFrame) {
                        complete(Result.failure(IllegalStateException(
                            "Threads 动态页面连接失败：${error.description}",
                        )))
                    }
                }
            }
            continuation.invokeOnCancellation {
                handler.post { complete(Result.failure(it ?: InterruptedException("分析已取消"))) }
            }
            handler.postDelayed(timeout, TIMEOUT_MS)
            webView.loadUrl(url)
        }
    }

    internal fun parseExtraction(raw: String): AnalyzedMedia? {
        if (raw == "null" || raw.isBlank()) return null
        val outer = mapper.readTree(raw)
        val root = if (outer.isTextual) mapper.readTree(outer.asText()) else outer
        if (!root.path("verified").asBoolean(false)) return null
        val permalink = root.path("permalink").asText()
        if (!isCanonicalPostUrl(permalink)) return null
        val title = root.path("title").asText().replace(Regex("\\s+"), " ").trim().take(72)
            .ifBlank { "Threads video" }
        val formats = root.path("videos").takeIf { it.isArray }.orEmpty().mapIndexedNotNull { index, item ->
            val mediaUrl = item.path("url").asText()
            if (!isAllowedMediaUrl(mediaUrl)) return@mapIndexedNotNull null
            DownloadFormat(
                id = "threads_web_${index + 1}",
                label = if (index == 0) "动态页面视频" else "视频 ${index + 1}",
                selector = "direct",
                directUrl = mediaUrl,
            )
        }.distinctBy(DownloadFormat::directUrl)
        return formats.takeIf(List<DownloadFormat>::isNotEmpty)?.let { AnalyzedMedia(title, it) }
    }

    private fun isThreadsUrl(url: String): Boolean = runCatching {
        val uri = URI(url)
        uri.scheme == "https" && uri.host.lowercase() in setOf(
            "threads.com", "www.threads.com", "threads.net", "www.threads.net",
        )
    }.getOrDefault(false)

    private fun isCanonicalPostUrl(url: String): Boolean = runCatching {
        isThreadsUrl(url) && URI(url).path.matches(Regex("/[^/]+/post/[^/]+/?"))
    }.getOrDefault(false)

    private fun isAllowedMediaUrl(url: String): Boolean = runCatching {
        val uri = URI(url)
        val host = uri.host.lowercase()
        uri.scheme == "https" && (
            host == "cdninstagram.com" || host.endsWith(".cdninstagram.com") ||
                host == "fbcdn.net" || host.endsWith(".fbcdn.net")
            )
    }.getOrDefault(false)

    private fun com.fasterxml.jackson.databind.JsonNode?.orEmpty(): List<com.fasterxml.jackson.databind.JsonNode> =
        if (this != null && isArray) toList() else emptyList()

    private const val EXTRACT_SCRIPT = """
        (() => {
          const marker = [...document.querySelectorAll('a[href]')].find(a => {
            const href = a.href || '';
            return href.includes('xmt=') && href.includes('injected_media_ids');
          });
          if (!marker) return null;
          const links = [...document.querySelectorAll('a[href*="/post/"]')];
          for (const link of links) {
            let node = link;
            for (let depth = 0; node && depth < 14; depth++, node = node.parentElement) {
              const videos = [...node.querySelectorAll('video')];
              if (!videos.length) continue;
              const urls = [];
              for (const video of videos) {
                for (const candidate of [video.currentSrc, video.src, ...[...video.querySelectorAll('source')].map(s => s.src)]) {
                  if (candidate && /^https:\/\//.test(candidate) &&
                      /(cdninstagram\.com|fbcdn\.net)(?:\/|$)/.test(new URL(candidate).hostname + '/')) {
                    urls.push(candidate);
                  }
                }
              }
              if (urls.length) return JSON.stringify({
                verified: true,
                permalink: link.href,
                title: (node.innerText || document.title || 'Threads video').slice(0, 200),
                videos: [...new Set(urls)].map(url => ({url}))
              });
            }
          }
          return null;
        })()
    """
}
