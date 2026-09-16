package com.videoget.app.downloads

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ContentValues
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

class DownloadWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    private val processId = id.toString()
    private val notifications = appContext.getSystemService(NotificationManager::class.java)

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val requestId = inputData.getString(KEY_REQUEST_ID)
        try {
            val request = requestId?.let { DownloadRequestStore.load(applicationContext, it) }
                ?: StoredDownloadRequest(
                    url = inputData.getString(KEY_URL) ?: error("缺少下载链接"),
                    selector = inputData.getString(KEY_FORMAT) ?: "best",
                    directUrl = inputData.getString(KEY_DIRECT_URL),
                    title = inputData.getString(KEY_TITLE).orEmpty(),
                )
            val workDir = File(applicationContext.cacheDir, "downloads/$processId").apply { mkdirs() }
            createChannel()
            setForeground(foreground(0, request.title.ifBlank { "准备下载" }))
            val media = if (request.directUrl != null) {
                downloadDirect(request.directUrl, workDir, request.title)
            } else {
                downloadWithYtDlp(request.url, request.selector, workDir, request.title)
            }
            val saved = publish(media)
            workDir.deleteRecursively()
            notifications.notify(NOTIFICATION_ID, notification(100, "下载完成"))
            Result.success(Data.Builder().putString(KEY_OUTPUT, saved).build())
        } catch (error: Exception) {
            Result.failure(failureData(error.message ?: "下载失败"))
        } finally {
            DownloadRequestStore.delete(applicationContext, requestId)
        }
    }

    private fun downloadWithYtDlp(url: String, selector: String, workDir: File, title: String): File {
        YoutubeDL.getInstance().init(applicationContext)
        FFmpeg.getInstance().init(applicationContext)
        val request = YoutubeDLRequest(url).apply {
            addOption("--no-playlist")
            addOption("--newline")
            addOption("--restrict-filenames")
            addOption("--socket-timeout", "45")
            addOption("--retries", "5")
            addOption("--fragment-retries", "5")
            addOption("--extractor-retries", "3")
            addOption("--retry-sleep", "http:linear=2::10")
            addOption("--merge-output-format", "mp4")
            addOption("-f", selector)
            addOption("-o", File(workDir, "video-get-%(id)s.%(ext)s").absolutePath)
        }
        YoutubeDL.getInstance().execute(request, processId) { progress, eta, _ ->
            if (isStopped) YoutubeDL.getInstance().destroyProcessById(processId)
            reportProgress(progress.toInt().coerceIn(0, 100), eta, title)
        }
        return workDir.listFiles()
            ?.filter { it.isFile && it.extension.lowercase() in MEDIA_EXTENSIONS }
            ?.maxByOrNull(File::length)
            ?: error("下载完成，但没有找到媒体文件")
    }

    private fun downloadDirect(rawUrl: String, workDir: File, title: String): File {
        val uri = URI(rawUrl)
        val host = uri.host.lowercase()
        val allowedHost = host == "video.twimg.com" ||
            host == "cdninstagram.com" || host.endsWith(".cdninstagram.com") ||
            host == "fbcdn.net" || host.endsWith(".fbcdn.net")
        require(uri.scheme == "https" && allowedHost) {
            "拒绝非受信任媒体地址"
        }
        val output = File(workDir, "video-get-${System.currentTimeMillis()}.mp4")
        val connection = URL(rawUrl).openConnection() as HttpURLConnection
        try {
            connection.instanceFollowRedirects = true
            connection.connectTimeout = 30_000
            connection.readTimeout = 60_000
            connection.setRequestProperty("User-Agent", "VideoGet/0.1 (+https://github.com/Ruefmiia/video-get)")
            if (host.endsWith("cdninstagram.com") || host.endsWith("fbcdn.net")) {
                connection.setRequestProperty("Referer", "https://www.threads.com/")
            }
            if (connection.responseCode !in 200..299) error("视频服务器 HTTP ${connection.responseCode}")
            val total = connection.contentLengthLong
            connection.inputStream.use { input ->
                output.outputStream().buffered().use { sink ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var downloaded = 0L
                    while (true) {
                        if (isStopped) error("下载已取消")
                        val count = input.read(buffer)
                        if (count < 0) break
                        sink.write(buffer, 0, count)
                        downloaded += count
                        val percent = if (total > 0) ((downloaded * 100) / total).toInt().coerceIn(0, 99) else 0
                        reportProgress(percent, 0, title)
                    }
                }
            }
            if (output.length() == 0L) error("视频服务器返回了空文件")
            return output
        } finally {
            connection.disconnect()
        }
    }

    private fun reportProgress(percent: Int, eta: Long, title: String) {
        setProgressAsync(Data.Builder().putInt(KEY_PROGRESS, percent).putLong(KEY_ETA, eta).build())
        notifications.notify(NOTIFICATION_ID, notification(percent, title.ifBlank { "正在下载" }))
    }

    private fun publish(source: File): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            val targetDir = applicationContext.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
                ?: error("无法访问视频目录")
            val target = File(targetDir, source.name)
            source.copyTo(target, overwrite = true)
            return target.absolutePath
        }
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, source.name)
            put(MediaStore.Video.Media.MIME_TYPE, if (source.extension.equals("webm", true)) "video/webm" else "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/Video Get")
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val resolver = applicationContext.contentResolver
        val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("无法在系统媒体库创建文件")
        try {
            resolver.openOutputStream(uri)?.use { output -> source.inputStream().use { it.copyTo(output) } }
                ?: error("无法写入系统媒体库")
            values.clear()
            values.put(MediaStore.Video.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            return uri.toString()
        } catch (error: Exception) {
            resolver.delete(uri, null, null)
            throw error
        }
    }

    private fun createChannel() {
        notifications.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "视频下载", NotificationManager.IMPORTANCE_LOW),
        )
    }

    private fun foreground(progress: Int, text: String) = ForegroundInfo(
        NOTIFICATION_ID,
        notification(progress, text),
        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
    )

    private fun notification(progress: Int, text: String) = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.stat_sys_download)
        .setContentTitle("Video Get")
        .setContentText(text)
        .setOnlyAlertOnce(true)
        .setOngoing(progress < 100)
        .setProgress(100, progress, progress == 0)
        .build()

    private fun failureData(message: String) = Data.Builder().putString(KEY_ERROR, message.take(500)).build()

    companion object {
        const val KEY_URL = "url"
        const val KEY_FORMAT = "format"
        const val KEY_DIRECT_URL = "direct_url"
        const val KEY_TITLE = "title"
        const val KEY_PROGRESS = "progress"
        const val KEY_ETA = "eta"
        const val KEY_OUTPUT = "output"
        const val KEY_ERROR = "error"
        const val KEY_REQUEST_ID = "request_id"
        private const val CHANNEL_ID = "video_downloads"
        private const val NOTIFICATION_ID = 17382
        private val MEDIA_EXTENSIONS = setOf("mp4", "mkv", "webm", "mov")
    }
}
