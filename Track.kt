package com.hudsify.app.model

data class Track(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val uri: String,
    val durationMs: Long,
    val artworkUri: String? = null
)
