package com.videoget.app.downloads

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.fasterxml.jackson.databind.ObjectMapper
import com.videoget.app.instagram.InstagramSession
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
    private val mapper = ObjectMapper()

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val requestId = inputData.getString(KEY_REQUEST_ID)
        var failureStage = FAILURE_STAGE_DOWNLOAD
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
            val items = request.items.ifEmpty {
                listOf(StoredDownloadItem("legacy", request.selector, request.directUrl))
            }
            val savedItems = mutableListOf<PublishedMedia>()
            val errors = mutableListOf<String>()
            items.forEachIndexed { index, item ->
                if (isStopped) error("下载已取消")
                val itemDir = File(workDir, "item-${index + 1}").apply { mkdirs() }
                try {
                    failureStage = FAILURE_STAGE_DOWNLOAD
                    val media = if (item.directUrl != null) {
                        downloadDirect(item.directUrl, itemDir, request.title, index, items.size)
                    } else {
                        downloadWithYtDlp(request.url, item.selector, itemDir, request.title, index, items.size)
                    }
                    failureStage = FAILURE_STAGE_SAVE
                    savedItems += publish(media)
                } catch (error: Exception) {
                    if (isStopped) throw error
                    errors += finalErrorMessage(error)
                }
            }
            workDir.deleteRecursively()
            if (savedItems.isEmpty()) error(errors.firstOrNull() ?: "下载失败")
            val first = savedItems.first()
            val completionText = if (errors.isEmpty()) {
                if (savedItems.size > 1) "${savedItems.size} 个视频下载完成" else "下载完成"
            } else {
                "成功 ${savedItems.size} 个，失败 ${errors.size} 个"
            }
            notifications.notify(NOTIFICATION_ID, notification(100, completionText, first))
            Result.success(
                Data.Builder()
                    .putString(KEY_OUTPUT_URI, first.uri)
                    .putString(KEY_OUTPUT_NAME, first.displayName)
                    .putString(KEY_OUTPUT_RELATIVE_PATH, first.relativePath)
                    .putString(KEY_OUTPUT_MIME_TYPE, first.mimeType)
                    .putString(KEY_OUTPUT_ITEMS, mapper.writeValueAsString(savedItems))
                    .putInt(KEY_FAILED_COUNT, errors.size)
                    .build(),
            )
        } catch (error: Exception) {
            Result.failure(failureData(finalErrorMessage(error), failureStage))
        } finally {
            DownloadRequestStore.delete(applicationContext, requestId)
        }
    }

    private fun downloadWithYtDlp(
        url: String,
        selector: String,
        workDir: File,
        title: String,
        itemIndex: Int,
        itemCount: Int,
    ): File {
        YoutubeDL.getInstance().init(applicationContext)
        FFmpeg.getInstance().init(applicationContext)
        if (isYouTubeUrl(url)) YouTubeEngineManager.prepare(applicationContext)
        if (isInstagramUrl(url) && InstagramSession.isLoggedIn()) {
            return InstagramSession.withYtDlpCookies(applicationContext) { cookieFile, userAgent ->
                executeYtDlp(url, selector, workDir, title, itemIndex, itemCount, cookieFile.absolutePath, userAgent)
            }
        }
        return executeYtDlp(url, selector, workDir, title, itemIndex, itemCount, null, null)
    }

    private fun executeYtDlp(
        url: String,
        selector: String,
        workDir: File,
        title: String,
        itemIndex: Int,
        itemCount: Int,
        cookiePath: String?,
        userAgent: String?,
    ): File {
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
            if (cookiePath != null) addOption("--cookies", cookiePath)
            if (userAgent != null) addOption("--user-agent", userAgent)
            if (isYouTubeUrl(url)) addOption("--remote-components", "ejs:github")
            addOption("-f", selector)
            addOption("-o", File(workDir, "video-get-%(id)s.%(ext)s").absolutePath)
        }
        YoutubeDL.getInstance().execute(request, processId) { progress, eta, output ->
            if (isStopped) YoutubeDL.getInstance().destroyProcessById(processId)
            val processing = output.contains("[Merger]", ignoreCase = true) ||
                output.contains("Merging formats", ignoreCase = true) ||
                output.contains("Post-process", ignoreCase = true)
            reportProgress(
                percent = overallProgress(itemIndex, itemCount, if (processing) 99 else progress.toInt()),
                eta = eta,
                title = title,
                stageLabel = itemLabel(itemIndex, itemCount, if (processing) "正在合并音视频" else "正在下载"),
            )
        }
        return workDir.listFiles()
            ?.filter { it.isFile && it.extension.lowercase() in MEDIA_EXTENSIONS }
            ?.maxByOrNull(File::length)
            ?: error("下载完成，但没有找到媒体文件")
    }

    private fun downloadDirect(
        rawUrl: String,
        workDir: File,
        title: String,
        itemIndex: Int,
        itemCount: Int,
    ): File {
        val uri = URI(rawUrl)
        val host = uri.host.lowercase()
        val allowedHost = host == "video.twimg.com" ||
            host == "cdninstagram.com" || host.endsWith(".cdninstagram.com") ||
            host == "fbcdn.net" || host.endsWith(".fbcdn.net")
        require(uri.scheme == "https" && allowedHost) {
            "拒绝非受信任媒体地址"
        }
        val connection = URL(rawUrl).openConnection() as HttpURLConnection
        try {
            connection.instanceFollowRedirects = true
            connection.connectTimeout = 30_000
            connection.readTimeout = 60_000
            connection.setRequestProperty("User-Agent", "VideoGet/0.1 (+https://github.com/Ruefmiia/video-get)")
            if (host.endsWith("cdninstagram.com") || host.endsWith("fbcdn.net")) {
                connection.setRequestProperty("Referer", "https://www.threads.com/")
            }
            if (connection.responseCode !in 200..299) error("媒体服务器 HTTP ${connection.responseCode}")
            val extension = extensionForContentType(connection.contentType, rawUrl)
            val output = File(workDir, "video-get-${itemIndex + 1}-${System.currentTimeMillis()}.$extension")
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
                        reportProgress(
                            overallProgress(itemIndex, itemCount, percent),
                            0,
                            title,
                            itemLabel(itemIndex, itemCount, "正在下载"),
                        )
                    }
                }
            }
            if (output.length() == 0L) error("媒体服务器返回了空文件")
            return output
        } finally {
            connection.disconnect()
        }
    }

    private fun reportProgress(
        percent: Int,
        eta: Long,
        title: String,
        stageLabel: String = "正在下载",
    ) {
        setProgressAsync(
            Data.Builder()
                .putInt(KEY_PROGRESS, percent)
                .putLong(KEY_ETA, eta)
                .putString(KEY_PROGRESS_LABEL, stageLabel)
                .build(),
        )
        val notificationText = title.ifBlank { stageLabel }
        notifications.notify(NOTIFICATION_ID, notification(percent, notificationText))
    }

    private fun overallProgress(itemIndex: Int, itemCount: Int, itemProgress: Int): Int =
        (((itemIndex * 100) + itemProgress.coerceIn(0, 100)) / itemCount.coerceAtLeast(1))
            .coerceIn(0, 99)

    private fun itemLabel(itemIndex: Int, itemCount: Int, action: String): String =
        if (itemCount > 1) "$action ${itemIndex + 1}/$itemCount" else action

    private fun isYouTubeUrl(url: String): Boolean = runCatching {
        URI(url).host.lowercase() in setOf(
            "youtube.com", "www.youtube.com", "m.youtube.com", "youtu.be", "www.youtu.be",
        )
    }.getOrDefault(false)

    private fun isInstagramUrl(url: String): Boolean = runCatching {
        URI(url).host.lowercase() in setOf("instagram.com", "www.instagram.com")
    }.getOrDefault(false)

    private fun publish(source: File): PublishedMedia {
        val mimeType = mimeType(source.extension)
        val isImage = mimeType.startsWith("image/")
        val directoryType = if (isImage) Environment.DIRECTORY_PICTURES else Environment.DIRECTORY_MOVIES
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            val moviesDir = applicationContext.getExternalFilesDir(directoryType)
                ?: error("无法访问视频目录")
            val targetDir = File(moviesDir, "Video Get").apply { mkdirs() }
            val target = File(targetDir, source.name)
            source.copyTo(target, overwrite = true)
            return PublishedMedia(
                uri = Uri.fromFile(target).toString(),
                displayName = target.name,
                relativePath = "${if (isImage) "Pictures" else "Movies"}/Video Get",
                mimeType = mimeType,
            )
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, source.name)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, "$directoryType/Video Get")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val resolver = applicationContext.contentResolver
        val collection = if (isImage) MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        else MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val uri = resolver.insert(collection, values)
            ?: error("无法在系统媒体库创建文件")
        try {
            resolver.openOutputStream(uri)?.use { output -> source.inputStream().use { it.copyTo(output) } }
                ?: error("无法写入系统媒体库")
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            return PublishedMedia(
                uri = uri.toString(),
                displayName = source.name,
                relativePath = "${if (isImage) "Pictures" else "Movies"}/Video Get",
                mimeType = mimeType,
            )
        } catch (error: Exception) {
            resolver.delete(uri, null, null)
            throw error
        }
    }

    private fun extensionForContentType(contentType: String?, rawUrl: String): String = when {
        contentType?.contains("image/jpeg", true) == true -> "jpg"
        contentType?.contains("image/png", true) == true -> "png"
        contentType?.contains("image/webp", true) == true -> "webp"
        contentType?.contains("image/avif", true) == true -> "avif"
        contentType?.contains("video/webm", true) == true -> "webm"
        else -> runCatching { URI(rawUrl).path.substringAfterLast('.').lowercase() }
            .getOrNull()?.takeIf { it in setOf("jpg", "jpeg", "png", "webp", "avif", "mp4", "webm") }
            ?: "mp4"
    }

    private fun mimeType(extension: String): String = when (extension.lowercase()) {
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "webp" -> "image/webp"
        "avif" -> "image/avif"
        "webm" -> "video/webm"
        else -> "video/mp4"
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

    private fun notification(progress: Int, text: String, saved: PublishedMedia? = null) =
        NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Video Get")
            .setContentText(text)
            .setOnlyAlertOnce(true)
            .setOngoing(progress < 100)
            .setAutoCancel(progress >= 100)
            .setProgress(100, progress, progress == 0)
            .setContentIntent(contentIntent(saved))
            .build()

    private fun contentIntent(saved: PublishedMedia?): PendingIntent? {
        val intent = if (saved != null) {
            val uri = Uri.parse(saved.uri).let { parsed ->
                if (parsed.scheme == "file") {
                    FileProvider.getUriForFile(
                        applicationContext,
                        "${applicationContext.packageName}.files",
                        File(requireNotNull(parsed.path)),
                    )
                } else {
                    parsed
                }
            }
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, saved.mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        } else {
            applicationContext.packageManager.getLaunchIntentForPackage(applicationContext.packageName)
        } ?: return null
        return PendingIntent.getActivity(
            applicationContext,
            if (saved == null) 0 else 1,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun failureData(message: String, stage: String) = Data.Builder()
        .putString(KEY_ERROR, message.take(500))
        .putString(KEY_FAILURE_STAGE, stage)
        .build()

    private fun finalErrorMessage(error: Exception): String {
        val message = error.message.orEmpty().trim()
        val finalError = message.substringAfterLast("ERROR:", missingDelimiterValue = "").trim()
        val lastLine = message.lineSequence().lastOrNull { it.isNotBlank() }?.trim()
        return finalError.ifBlank { lastLine ?: "下载失败" }
    }

    private data class PublishedMedia(
        val uri: String,
        val displayName: String,
        val relativePath: String,
        val mimeType: String,
    )

    companion object {
        const val KEY_URL = "url"
        const val KEY_FORMAT = "format"
        const val KEY_DIRECT_URL = "direct_url"
        const val KEY_TITLE = "title"
        const val KEY_PROGRESS = "progress"
        const val KEY_PROGRESS_LABEL = "progress_label"
        const val KEY_ETA = "eta"
        const val KEY_OUTPUT_URI = "output_uri"
        const val KEY_OUTPUT_NAME = "output_name"
        const val KEY_OUTPUT_RELATIVE_PATH = "output_relative_path"
        const val KEY_OUTPUT_MIME_TYPE = "output_mime_type"
        const val KEY_OUTPUT_ITEMS = "output_items"
        const val KEY_FAILED_COUNT = "failed_count"
        const val KEY_ERROR = "error"
        const val KEY_FAILURE_STAGE = "failure_stage"
        const val KEY_REQUEST_ID = "request_id"
        const val ACTIVE_WORK_TAG = "video_get_active_download"
        const val FAILURE_STAGE_DOWNLOAD = "download"
        const val FAILURE_STAGE_SAVE = "save"
        private const val CHANNEL_ID = "video_downloads"
        private const val NOTIFICATION_ID = 17382
        private val MEDIA_EXTENSIONS = setOf("mp4", "mkv", "webm", "mov")
    }
}
