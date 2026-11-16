package com.sp.player.player

import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.os.Build
import android.provider.MediaStore

/** MediaStore scan of audio + video. No background service; scan on launch/resume only. */
object MediaLibrary {

    private val EXCLUDED_AUDIO_FOLDERS = setOf("ringtones", "notifications", "alarms")

    private fun Cursor.longOr(name: String, def: Long = 0L): Long {
        val i = getColumnIndex(name)
        return if (i >= 0) getLong(i) else def
    }

    private fun Cursor.stringOr(name: String): String? {
        val i = getColumnIndex(name)
        return if (i >= 0) getString(i) else null
    }

    fun scanAudio(context: Context): List<Track> {
        val tracks = mutableListOf<Track>()
        val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = mutableListOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.DATA
        )
        if (Build.VERSION.SDK_INT >= 29) projection.add(MediaStore.Audio.Media.RELATIVE_PATH)
        context.contentResolver.query(uri, projection.toTypedArray(), null, null, null)?.use { c ->
            while (c.moveToNext()) {
                val duration = c.longOr(MediaStore.Audio.Media.DURATION)
                if (duration <= 0) continue
                val id = c.longOr(MediaStore.Audio.Media._ID)
                val dataPath = c.stringOr(MediaStore.Audio.Media.DATA)
                val relPath = if (Build.VERSION.SDK_INT >= 29) {
                    c.stringOr(MediaStore.Audio.Media.RELATIVE_PATH)
                } else null
                val folder = folderOf(relPath, dataPath)
                if (EXCLUDED_AUDIO_FOLDERS.contains(folder.lowercase())) continue
                val title = c.stringOr(MediaStore.Audio.Media.TITLE)?.takeIf { it.isNotBlank() }
                    ?: c.stringOr(MediaStore.Audio.Media.DISPLAY_NAME)?.substringBeforeLast('.')
                    ?: "Unknown"
                tracks += Track(
                    id = id,
                    title = title,
                    // MediaStore reports "<unknown>" for files without an
                    // artist tag — show nothing instead of the literal text.
                    artist = (c.stringOr(MediaStore.Audio.Media.ARTIST) ?: "")
                        .let { if (it == "<unknown>") "" else it },
                    uri = ContentUris.withAppendedId(uri, id),
                    dataPath = dataPath,
                    durationMs = duration,
                    sizeBytes = c.longOr(MediaStore.Audio.Media.SIZE),
                    dateAddedSec = c.longOr(MediaStore.Audio.Media.DATE_ADDED),
                    folder = folder,
                    isVideo = false
                )
            }
        }
        return tracks
    }

    fun scanVideo(context: Context): List<Track> {
        val tracks = mutableListOf<Track>()
        val uri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val projection = mutableListOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.TITLE,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.DATA
        )
        if (Build.VERSION.SDK_INT >= 29) {
            projection.add(MediaStore.Video.Media.RELATIVE_PATH)
            projection.add(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
        }
        context.contentResolver.query(uri, projection.toTypedArray(), null, null, null)?.use { c ->
            while (c.moveToNext()) {
                val duration = c.longOr(MediaStore.Video.Media.DURATION)
                if (duration <= 0) continue
                val id = c.longOr(MediaStore.Video.Media._ID)
                val dataPath = c.stringOr(MediaStore.Video.Media.DATA)
                val relPath = if (Build.VERSION.SDK_INT >= 29) {
                    c.stringOr(MediaStore.Video.Media.RELATIVE_PATH)
                } else null
                val bucket = if (Build.VERSION.SDK_INT >= 29) {
                    c.stringOr(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)?.takeIf { it.isNotBlank() }
                } else null
                val folder = bucket ?: folderOf(relPath, dataPath)
                val title = c.stringOr(MediaStore.Video.Media.TITLE)?.takeIf { it.isNotBlank() }
                    ?: c.stringOr(MediaStore.Video.Media.DISPLAY_NAME)?.substringBeforeLast('.')
                    ?: "Unknown"
                tracks += Track(
                    id = id,
                    title = title,
                    artist = "",
                    uri = ContentUris.withAppendedId(uri, id),
                    dataPath = dataPath,
                    durationMs = duration,
                    sizeBytes = c.longOr(MediaStore.Video.Media.SIZE),
                    dateAddedSec = c.longOr(MediaStore.Video.Media.DATE_ADDED),
                    folder = folder,
                    isVideo = true
                )
            }
        }
        return tracks
    }

    fun foldersOf(tracks: List<Track>): List<Folder> =
        tracks.groupBy { it.folder }
            .map { Folder(it.key, it.value) }
            .sortedBy { it.name.lowercase() }

    private fun folderOf(relPath: String?, dataPath: String?): String {
        val rel = relPath?.trim()?.trimEnd('/')?.takeIf { it.isNotEmpty() }
        if (rel != null) {
            val last = rel.substringAfterLast('/', rel)
            if (last.isNotEmpty()) return last
        }
        val parent = dataPath?.substringBeforeLast('/', "")?.substringAfterLast('/', "")
        if (!parent.isNullOrEmpty()) return parent
        return "Internal Storage"
    }
}
