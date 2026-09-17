package com.videoget.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.core.content.FileProvider
import com.videoget.app.CompletedDownload
import java.io.File

object MediaActions {
    fun openVideo(context: Context, media: CompletedDownload): Boolean = runCatching {
        val uri = shareableUri(context, media.contentUri)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, media.mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }.isSuccess

    fun openLocation(context: Context): Boolean {
        val directoryUri = Uri.parse(
            "content://com.android.externalstorage.documents/document/primary%3AMovies%2FVideo%20Get",
        )
        val viewDirectory = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(directoryUri, DocumentsContract.Document.MIME_TYPE_DIR)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(viewDirectory)
            return true
        } catch (_: Exception) {
            val picker = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                type = "video/*"
                addCategory(Intent.CATEGORY_OPENABLE)
                putExtra(DocumentsContract.EXTRA_INITIAL_URI, directoryUri)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            return runCatching { context.startActivity(picker) }.isSuccess
        }
    }

    private fun shareableUri(context: Context, rawUri: String): Uri {
        val parsed = Uri.parse(rawUri)
        return if (parsed.scheme == "file") {
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.files",
                File(requireNotNull(parsed.path)),
            )
        } else {
            parsed
        }
    }
}
