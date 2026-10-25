package com.hudsify.app.data

import android.content.ContentResolver
import android.provider.MediaStore
import com.hudsify.app.model.Track

class AudioRepository(private val resolver: ContentResolver) {
    fun loadTracks(): List<Track> {
        val result = mutableListOf<Track>()
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.ALBUM_ID
        )
        resolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            "${MediaStore.Audio.Media.IS_MUSIC} != 0",
            null,
            "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"
        )?.use { c ->
            val id = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val title = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artist = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val album = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val duration = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val albumId = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            while (c.moveToNext()) {
                val mediaId = c.getLong(id)
                result += Track(
                    mediaId,
                    c.getString(title) ?: "Unknown title",
                    c.getString(artist) ?: "Unknown artist",
                    c.getString(album) ?: "Unknown album",
                    "${MediaStore.Audio.Media.EXTERNAL_CONTENT_URI}/$mediaId",
                    c.getLong(duration),
                    "content://media/external/audio/albumart/${c.getLong(albumId)}"
                )
            }
        }
        return result
    }
}
