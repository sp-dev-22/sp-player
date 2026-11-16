package com.sp.player.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.util.Size
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.TextView
import com.sp.player.R
import com.sp.player.player.PlayerManager
import com.sp.player.player.Prefs
import com.sp.player.player.Track
import com.sp.player.player.formatDuration
import com.sp.player.player.formatSize
import java.util.concurrent.Executors

/** List rows for audio/video tracks. Video rows show system MediaStore thumbnails;
 *  audio rows show a plain music icon (no artwork — keeps lists light). */
class TrackAdapter(context: Context, val items: List<Track>) : BaseAdapter() {

    companion object {
        // Shared across adapter instances so re-scans don't spawn new threads.
        private val thumbCache = object : LinkedHashMap<Long, Bitmap>(64, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, Bitmap>): Boolean {
                return size > 40
            }
        }
        private val thumbExecutor = Executors.newSingleThreadExecutor()
    }

    private val inflater = LayoutInflater.from(context)
    private val appContext = context.applicationContext
    private val defaultTitleColor = ThemeTint.textPrimary(context)
    // v1.2.0 optimization: the accent and the now-playing highlight used to
    // be read from SharedPreferences (and re-blended) for EVERY row bind.
    // Adapters are recreated on every list refresh, so caching them here
    // is always fresh — and scrolling no longer touches disk per row.
    private val accent = Prefs.accent(context)
    private val playingHighlight =
        ThemeTint.accentBackground(context, accent, 0.16f)

    /** Select mode (v1.1.0): set by MainActivity; rows show a checkbox bound
     *  to [selection] (track keys, stable across sort/filter). */
    var selectMode: Boolean = false
    var selection: Set<String> = emptySet()

    override fun getCount(): Int = items.size
    override fun getItem(position: Int): Any = items[position]
    override fun getItemId(position: Int): Long = items[position].id

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: inflater.inflate(R.layout.item_track, parent, false)
        val holder = (view.tag as? Holder) ?: Holder(view).also { view.tag = it }
        val track = items[position]
        holder.title.text = track.title
        val sub = buildString {
            append(formatDuration(track.durationMs))
            append("  •  ")
            append(formatSize(track.sizeBytes))
            if (track.isVideo) {
                append("  •  ")
                append(track.folder)
            } else if (track.artist.isNotBlank()) {
                append("  •  ")
                append(track.artist)
            }
        }
        holder.subtitle.text = sub
        if (track.isVideo) {
            holder.thumb.setImageResource(R.drawable.ic_video_placeholder)
            holder.thumb.tag = track.id
            loadThumbnail(track, holder.thumb)
        } else {
            holder.thumb.tag = null
            holder.thumb.setImageResource(R.drawable.ic_music)
        }
        // Now-playing highlight: accent-tinted row + accent bold title.
        // Rows are recycled, so the else branch must reset both every time.
        if (PlayerManager.current?.key == track.key) {
            view.setBackgroundColor(playingHighlight)
            holder.title.setTextColor(accent)
            holder.title.setTypeface(null, Typeface.BOLD)
        } else {
            view.setBackgroundColor(Color.TRANSPARENT)
            holder.title.setTextColor(defaultTitleColor)
            holder.title.setTypeface(null, Typeface.NORMAL)
        }
        // Select-mode checkbox (recycled rows: set both branches every time).
        holder.selectBox.visibility = if (selectMode) View.VISIBLE else View.GONE
        holder.selectBox.isChecked = selection.contains(track.key)
        return view
    }

    private fun loadThumbnail(track: Track, imageView: ImageView) {
        synchronized(thumbCache) {
            thumbCache[track.id]?.let {
                imageView.setImageBitmap(it)
                return
            }
        }
        if (Build.VERSION.SDK_INT < 29) return
        thumbExecutor.execute {
            try {
                val bmp = appContext.contentResolver.loadThumbnail(
                    track.uri,
                    Size(160, 160),
                    null
                )
                synchronized(thumbCache) { thumbCache[track.id] = bmp }
                imageView.post {
                    if (imageView.tag == track.id) imageView.setImageBitmap(bmp)
                }
            } catch (_: Exception) {
                // Leave the placeholder.
            }
        }
    }

    private class Holder(view: View) {
        val thumb: ImageView = view.findViewById(R.id.track_thumb)
        val title: TextView = view.findViewById(R.id.track_title)
        val subtitle: TextView = view.findViewById(R.id.track_subtitle)
        val selectBox: CheckBox = view.findViewById(R.id.track_select)
    }
}
