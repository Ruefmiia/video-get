package com.videoget.app

import android.app.Application
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL

class VideoGetApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        YoutubeDL.getInstance().init(this)
        FFmpeg.getInstance().init(this)
    }
}
