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

data class Playlist(
    val id: Long,
    val name: String,
    val trackIds: List<Long>
)

data class LrcLine(val timeMs: Long, val text: String)

data class SubtitleTrack(
    val uri: String,
    val language: String,
    val label: String
)
