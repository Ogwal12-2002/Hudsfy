package com.hudsify.app.data

import android.content.Context
import com.hudsify.app.model.Playlist
import org.json.JSONArray
import org.json.JSONObject

class HudsifyStore(context: Context) {
    private val prefs = context.getSharedPreferences("hudsify", Context.MODE_PRIVATE)

    fun favorites(): Set<Long> =
        prefs.getStringSet("favorites", emptySet())!!.mapNotNull { it.toLongOrNull() }.toSet()

    fun setFavorites(ids: Set<Long>) =
        prefs.edit().putStringSet("favorites", ids.map { it.toString() }.toSet()).apply()

    fun playlists(): List<Playlist> {
        val raw = prefs.getString("playlists", "[]") ?: "[]"
        val a = JSONArray(raw)
        return buildList {
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                val ids = buildList {
                    val t = o.optJSONArray("trackIds") ?: JSONArray()
                    for (j in 0 until t.length()) add(t.getLong(j))
                }
                add(Playlist(o.getLong("id"), o.getString("name"), ids))
            }
        }
    }

    fun savePlaylists(items: List<Playlist>) {
        val a = JSONArray()
        items.forEach { p ->
            val o = JSONObject()
            o.put("id", p.id)
            o.put("name", p.name)
            o.put("trackIds", JSONArray(p.trackIds))
            a.put(o)
        }
        prefs.edit().putString("playlists", a.toString()).apply()
    }

    fun settings(key: String, fallback: Boolean = false) =
        prefs.getBoolean("setting_$key", fallback)

    fun setSetting(key: String, value: Boolean) =
        prefs.edit().putBoolean("setting_$key", value).apply()
}
