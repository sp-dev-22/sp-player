package com.sp.player.ui

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.TextView
import com.sp.player.R
import com.sp.player.player.Folder

/** Folder list for the Folders tab. First row (when not searching) is My Playlist. */
class FolderAdapter(
    context: Context,
    private val folders: List<Folder>,
    private val playlistCount: Int,
    private val showPlaylistRow: Boolean
) : BaseAdapter() {

    private val inflater = LayoutInflater.from(context)

    override fun getCount(): Int = folders.size + if (showPlaylistRow) 1 else 0

    override fun getItem(position: Int): Any =
        if (isPlaylistRow(position)) "playlist" else folderAt(position)

    override fun getItemId(position: Int): Long = position.toLong()

    fun isPlaylistRow(position: Int): Boolean = showPlaylistRow && position == 0

    fun folderAt(position: Int): Folder {
        val index = if (showPlaylistRow) position - 1 else position
        return folders[index]
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: inflater.inflate(R.layout.item_folder, parent, false)
        val nameView: TextView = view.findViewById(R.id.folder_name)
        val countView: TextView = view.findViewById(R.id.folder_count)
        if (isPlaylistRow(position)) {
            nameView.text = "My Playlist"
            countView.text = "$playlistCount songs/videos"
        } else {
            val folder = folderAt(position)
            nameView.text = folder.name
            countView.text = "${folder.tracks.size} files"
        }
        return view
    }
}
