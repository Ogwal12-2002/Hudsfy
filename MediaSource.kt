package com.hudsify.app.source

import com.hudsify.app.model.Track

/**
 * Adapter boundary for a music catalog that you are authorized to access.
 * Implementations can be local, self-hosted, or an external licensed API.
 */
interface MediaSource {
    suspend fun search(query: String): List<Track>
    suspend fun trending(): List<Track>
    suspend fun resolvePlayback(track: Track): String
}
