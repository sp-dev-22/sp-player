package com.sp.player.player

import android.net.Uri

/** One audio or video file from MediaStore. */
data class Track(
    val id: Long,
    val title: String,
    val artist: String,
    val uri: Uri,
    /** Filesystem path from the DATA column; null on newer Android versions. Best-effort only. */
    val dataPath: String?,
    val durationMs: Long,
    val sizeBytes: Long,
    val dateAddedSec: Long,
    val folder: String,
    val isVideo: Boolean
) {
    /** Stable key used by the playlist store: "A:<id>" or "V:<id>". */
    val key: String get() = (if (isVideo) "V" else "A") + ":" + id
}

/** A folder group in the Folders tab (audio + video combined). */
data class Folder(val name: String, val tracks: List<Track>)

fun formatDuration(ms: Long): String {
    if (ms <= 0) return "0:00"
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

fun formatSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return "%.0f KB".format(kb)
    val mb = kb / 1024.0
    if (mb < 1024) return "%.1f MB".format(mb)
    return "%.1f GB".format(mb / 1024.0)
}
