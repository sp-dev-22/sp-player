package com.sp.player.player

import android.content.Context

/** The ONE playlist ("My Playlist"), stored as track keys in SharedPreferences. */
object PlaylistStore {
    private const val NAME = "sp_player_prefs"
    private const val KEY = "playlist"

    fun entries(context: Context): List<String> {
        val raw = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .getString(KEY, "") ?: ""
        return raw.split(';').filter { it.isNotBlank() }
    }

    fun contains(context: Context, track: Track): Boolean =
        entries(context).contains(track.key)

    fun add(context: Context, track: Track) {
        val list = entries(context).toMutableList()
        if (!list.contains(track.key)) {
            list.add(track.key)
            save(context, list)
        }
    }

    fun remove(context: Context, track: Track) {
        val list = entries(context).toMutableList()
        if (list.remove(track.key)) save(context, list)
    }

    /** Resolve stored keys to tracks from the current library, keeping playlist order. */
    fun resolve(context: Context, all: List<Track>): List<Track> {
        val byKey = all.associateBy { it.key }
        return entries(context).mapNotNull { byKey[it] }
    }

    private fun save(context: Context, list: List<String>) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY, list.joinToString(";")).apply()
    }
}
