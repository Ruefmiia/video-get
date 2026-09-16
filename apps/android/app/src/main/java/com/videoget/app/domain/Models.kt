package com.videoget.app.domain

enum class PlatformId {
    X,
    INSTAGRAM,
    THREADS,
}

data class UrlMatch(
    val platform: PlatformId,
    val canonicalUrl: String,
    val available: Boolean,
)

enum class AnalyzeState {
    IDLE,
    ANALYZING,
    READY,
    PLANNED,
    DOWNLOADING,
    COMPLETED,
    INVALID,
}
