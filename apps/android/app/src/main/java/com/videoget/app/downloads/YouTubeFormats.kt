package com.videoget.app.downloads

object YouTubeFormats {
    val formats = listOf(
        DownloadFormat(
            id = "youtube_best",
            label = "最佳兼容画质（MP4）",
            selector = "bv*[vcodec^=avc1][ext=mp4]+ba[ext=m4a]/b[ext=mp4]/bv*+ba/b",
        ),
        DownloadFormat(
            id = "youtube_1080",
            label = "最高 1080p",
            selector = "bv*[height<=1080][vcodec^=avc1][ext=mp4]+ba[ext=m4a]/b[height<=1080][ext=mp4]/bv*[height<=1080]+ba/b[height<=1080]",
        ),
        DownloadFormat(
            id = "youtube_720",
            label = "最高 720p",
            selector = "bv*[height<=720][vcodec^=avc1][ext=mp4]+ba[ext=m4a]/b[height<=720][ext=mp4]/bv*[height<=720]+ba/b[height<=720]",
        ),
    )
}
