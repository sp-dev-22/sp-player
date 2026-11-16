package com.sp.player

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.AdapterView
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.sp.player.player.Folder
import com.sp.player.player.MediaLibrary
import com.sp.player.player.PlaylistStore
import com.sp.player.player.PlayerManager
import com.sp.player.player.Prefs
import com.sp.player.player.Track
import com.sp.player.ui.FolderAdapter
import com.sp.player.ui.ThemeTint
import com.sp.player.ui.TrackAdapter

class MainActivity : AppCompatActivity() {

    companion object {
        private const val REQ_MEDIA = 101
    }

    private enum class Mode { MUSIC, VIDEO, FOLDERS, FOLDER_DETAIL, PLAYLIST }

    private lateinit var headerTitle: TextView
    private lateinit var headerRow: LinearLayout
    private lateinit var tabsRow: LinearLayout
    private lateinit var listView: ListView
    private lateinit var searchBox: EditText
    private lateinit var permissionRow: LinearLayout
    private lateinit var permissionButton: Button
    private lateinit var miniPlayer: LinearLayout
    private lateinit var miniTitle: TextView
    private lateinit var miniPlayPause: ImageButton
    private lateinit var miniNext: ImageButton
    private lateinit var tabMusic: TextView
    private lateinit var tabVideo: TextView
    private lateinit var tabFolders: TextView
    private lateinit var sortButton: Button
    private lateinit var settingsButton: Button
    private lateinit var emptyText: TextView
    private lateinit var selectBar: LinearLayout
    private lateinit var selectActionButton: Button
    private lateinit var selectCancelButton: Button

    private var mode = Mode.MUSIC
    private var audioTracks: List<Track> = emptyList()
    private var videoTracks: List<Track> = emptyList()
    // v1.2.0: one shared view of the whole library, so callers stop
    // allocating a fresh concatenated list each time.
    private val allTracks: List<Track>
        get() = audioTracks + videoTracks
    private var folders: List<Folder> = emptyList()
    private var detailTracks: List<Track> = emptyList()
    private var detailTitle: String = ""
    private var query: String = ""
    private var lastPlayingKey: String? = null

    // Select mode (v1.1.0): selection holds track keys — stable across
    // sort/filter/tab switches. The bottom bar adds all selected tracks to
    // the playlist at once.
    private var selectMode = false
    private val selection = mutableSetOf<String>()

    // v1.2.0: the theme this instance was created with. If the accent
    // changed in Settings while we were away, onResume recreates so the
    // system widgets pick up the new theme too.
    private var appliedThemeRes = 0

    private val selectBackCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            exitSelectMode()
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private val miniPlayerUpdater = object : Runnable {
        override fun run() {
            refreshMiniPlayer()
            // Move the now-playing row highlight when the track changes.
            val key = PlayerManager.current?.key
            if (key != lastPlayingKey) {
                lastPlayingKey = key
                (listView.adapter as? TrackAdapter)?.notifyDataSetChanged()
            }
            handler.postDelayed(this, 500)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        applyNightMode()
        super.onCreate(savedInstanceState)
        // v1.2.0: pick the theme variant for the chosen accent before
        // inflating, so system widgets follow the theme too.
        appliedThemeRes = ThemeTint.themeFor(Prefs.accent(this))
        setTheme(appliedThemeRes)
        setContentView(R.layout.activity_main)

        headerTitle = findViewById(R.id.header_title)
        headerRow = findViewById(R.id.header_row)
        tabsRow = findViewById(R.id.tabs_row)
        listView = findViewById(R.id.list_view)
        searchBox = findViewById(R.id.search_box)
        permissionRow = findViewById(R.id.permission_row)
        permissionButton = findViewById(R.id.permission_button)
        miniPlayer = findViewById(R.id.mini_player)
        miniTitle = findViewById(R.id.mini_title)
        miniPlayPause = findViewById(R.id.mini_play_pause)
        miniNext = findViewById(R.id.mini_next)
        tabMusic = findViewById(R.id.tab_music)
        tabVideo = findViewById(R.id.tab_video)
        tabFolders = findViewById(R.id.tab_folders)
        sortButton = findViewById(R.id.sort_button)
        settingsButton = findViewById(R.id.settings_button)
        emptyText = findViewById(R.id.empty_text)
        selectBar = findViewById(R.id.select_bar)
        selectActionButton = findViewById(R.id.select_add_button)
        selectCancelButton = findViewById(R.id.select_cancel_button)

        tabMusic.setOnClickListener { switchMode(Mode.MUSIC) }
        tabVideo.setOnClickListener { switchMode(Mode.VIDEO) }
        tabFolders.setOnClickListener { switchMode(Mode.FOLDERS) }

        sortButton.setOnClickListener {
            val next = if (Prefs.sortBy(this) == Prefs.SORT_DATE) Prefs.SORT_NAME else Prefs.SORT_DATE
            Prefs.setSortBy(this, next)
            refreshList()
        }
        settingsButton.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        permissionButton.setOnClickListener {
            ActivityCompat.requestPermissions(this, mediaPermissions(), REQ_MEDIA)
        }

        searchBox.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                query = s?.toString() ?: ""
                refreshList()
            }
        })

        miniTitle.setOnClickListener {
            if (PlayerManager.current != null) {
                startActivity(Intent(this, PlayerActivity::class.java))
            }
        }
        miniPlayPause.setOnClickListener {
            PlayerManager.togglePlayPause()
            refreshMiniPlayer()
        }
        miniNext.setOnClickListener {
            PlayerManager.next()
            refreshMiniPlayer()
        }

        // v1.1.1: the bar's action depends on where you are — inside the
        // Playlist it removes, everywhere else it adds.
        selectActionButton.setOnClickListener {
            if (mode == Mode.PLAYLIST) removeSelectionFromPlaylist()
            else addSelectionToPlaylist()
        }
        selectCancelButton.setOnClickListener { exitSelectMode() }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (mode == Mode.FOLDER_DETAIL || mode == Mode.PLAYLIST) {
                    switchMode(Mode.FOLDERS)
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
        // Added after the folder back callback, so it takes priority when enabled.
        onBackPressedDispatcher.addCallback(this, selectBackCallback)

        refreshList()
    }

    override fun onResume() {
        super.onResume()
        // v1.2.0: theme changed in Settings while we were away — recreate
        // so dialogs/buttons pick up the new theme (applyAccent alone
        // only re-tints our own views). The recreate re-runs onResume
        // with the new theme, so this never loops.
        if (ThemeTint.themeFor(Prefs.accent(this)) != appliedThemeRes) {
            recreate()
            return
        }
        applyAccent()
        if (hasMediaPermission()) {
            permissionRow.visibility = View.GONE
            scanLibrary()
        } else {
            permissionRow.visibility = View.VISIBLE
            audioTracks = emptyList()
            videoTracks = emptyList()
            folders = emptyList()
            detailTracks = emptyList()
            refreshList()
        }
        refreshMiniPlayer()
        handler.post(miniPlayerUpdater)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(miniPlayerUpdater)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_MEDIA) {
            if (hasMediaPermission()) {
                permissionRow.visibility = View.GONE
                scanLibrary()
            } else {
                Toast.makeText(
                    this,
                    "Media permission is needed to show your library",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun applyNightMode() {
        val mode = when (Prefs.nightMode(this)) {
            Prefs.NIGHT_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            Prefs.NIGHT_DARK -> AppCompatDelegate.MODE_NIGHT_YES
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(mode)
    }

    private fun mediaPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= 33) {
            arrayOf(
                Manifest.permission.READ_MEDIA_AUDIO,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.POST_NOTIFICATIONS
            )
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    private fun hasMediaPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= 33) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO) ==
                PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_VIDEO) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) ==
                PackageManager.PERMISSION_GRANTED
        }
    }

    private fun scanLibrary() {
        Thread {
            val audio = MediaLibrary.scanAudio(this)
            val video = MediaLibrary.scanVideo(this)
            runOnUiThread {
                audioTracks = audio
                videoTracks = video
                folders = MediaLibrary.foldersOf(audio + video)
                if (mode == Mode.PLAYLIST) {
                    detailTracks = PlaylistStore.resolve(this, audio + video)
                } else if (mode == Mode.FOLDER_DETAIL) {
                    folders.find { it.name == detailTitle }?.let { detailTracks = it.tracks }
                }
                refreshList()
            }
        }.start()
    }

    private fun switchMode(newMode: Mode) {
        mode = newMode
        refreshList()
    }

    private fun sorted(tracks: List<Track>): List<Track> {
        return when (Prefs.sortBy(this)) {
            Prefs.SORT_NAME -> tracks.sortedBy { it.title.lowercase() }
            else -> tracks.sortedByDescending { it.dateAddedSec }
        }
    }

    private fun filtered(tracks: List<Track>): List<Track> {
        val q = query.trim().lowercase()
        val base = sorted(tracks)
        if (q.isEmpty()) return base
        return base.filter {
            it.title.lowercase().contains(q) || it.artist.lowercase().contains(q)
        }
    }

    private fun refreshList() {
        val accent = Prefs.accent(this)
        val secondary = ContextCompat.getColor(this, R.color.text_secondary)
        highlightTab(tabMusic, mode == Mode.MUSIC, accent, secondary)
        highlightTab(tabVideo, mode == Mode.VIDEO, accent, secondary)
        highlightTab(
            tabFolders,
            mode == Mode.FOLDERS || mode == Mode.FOLDER_DETAIL || mode == Mode.PLAYLIST,
            accent,
            secondary
        )
        sortButton.text =
            if (Prefs.sortBy(this) == Prefs.SORT_DATE) "Date: Newest first" else "Name: A-Z"
        headerTitle.text = when (mode) {
            Mode.FOLDER_DETAIL, Mode.PLAYLIST -> detailTitle
            else -> getString(R.string.app_name)
        }
        when (mode) {
            Mode.MUSIC -> {
                emptyText.text = "No songs found"
                showTracks(filtered(audioTracks))
            }
            Mode.VIDEO -> {
                emptyText.text = "No videos found"
                showTracks(filtered(videoTracks))
            }
            Mode.FOLDERS -> {
                emptyText.text = "No folders found"
                showFolders()
            }
            Mode.FOLDER_DETAIL -> {
                emptyText.text = "Nothing in this folder"
                showTracks(filtered(detailTracks))
            }
            Mode.PLAYLIST -> {
                emptyText.text = "Playlist is empty — long-press a song or video to add it"
                showTracks(filtered(detailTracks))
            }
        }
        updateSelectBar()
    }

    private fun highlightTab(tab: TextView, selected: Boolean, accent: Int, secondary: Int) {
        tab.setTextColor(if (selected) accent else secondary)
        tab.setTypeface(null, if (selected) Typeface.BOLD else Typeface.NORMAL)
    }

    private fun applyAccent() {
        val accent = Prefs.accent(this)
        // Rainbow theme: the mini-player shows the full gradient.
        if (Prefs.isRainbow(this)) {
            miniPlayer.background = ThemeTint.rainbowDrawable()
        } else {
            miniPlayer.setBackgroundColor(accent)
        }
        val tint = ThemeTint.accentBackground(this, accent, 0.10f)
        headerRow.setBackgroundColor(tint)
        tabsRow.setBackgroundColor(tint)
        selectBar.setBackgroundColor(tint)
        sortButton.setTextColor(accent)
        settingsButton.setTextColor(accent)
        selectActionButton.setTextColor(accent)
        miniPlayPause.imageTintList = ColorStateList.valueOf(
            ContextCompat.getColor(this, android.R.color.white)
        )
        miniNext.imageTintList = ColorStateList.valueOf(
            ContextCompat.getColor(this, android.R.color.white)
        )
    }

    private fun showTracks(tracks: List<Track>) {
        emptyText.visibility = if (tracks.isEmpty()) View.VISIBLE else View.GONE
        val adapter = TrackAdapter(this, tracks)
        adapter.selectMode = selectMode
        adapter.selection = selection
        listView.adapter = adapter
        listView.onItemClickListener =
            AdapterView.OnItemClickListener { _, _, position, _ ->
                val list = (listView.adapter as TrackAdapter).items
                val track = list[position]
                if (selectMode) {
                    toggleSelection(track)
                    return@OnItemClickListener
                }
                playFromList(list, position)
                if (track.isVideo) {
                    startActivity(Intent(this, PlayerActivity::class.java))
                } else {
                    refreshMiniPlayer()
                }
            }
        listView.onItemLongClickListener =
            AdapterView.OnItemLongClickListener { _, _, position, _ ->
                val list = (listView.adapter as TrackAdapter).items
                val track = list[position]
                if (selectMode) {
                    toggleSelection(track)
                } else {
                    showTrackOptions(track)
                }
                true
            }
    }

    private fun showFolders() {
        val q = query.trim().lowercase()
        val items = if (q.isEmpty()) folders else folders.filter {
            it.name.lowercase().contains(q)
        }
        emptyText.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        val adapter = FolderAdapter(this, items, PlaylistStore.entries(this).size, q.isEmpty())
        listView.adapter = adapter
        listView.onItemClickListener =
            AdapterView.OnItemClickListener { _, _, position, _ ->
                if (adapter.isPlaylistRow(position)) {
                    detailTitle = "My Playlist"
                    detailTracks = PlaylistStore.resolve(this, allTracks)
                    mode = Mode.PLAYLIST
                } else {
                    val folder = adapter.folderAt(position)
                    detailTitle = folder.name
                    detailTracks = folder.tracks
                    mode = Mode.FOLDER_DETAIL
                }
                refreshList()
            }
        listView.onItemLongClickListener = null
    }

    private fun playFromList(list: List<Track>, position: Int) {
        PlayerManager.play(this, list, position)
    }

    /**
     * Long-press menu (v1.1.0): replaces the old direct long-press playlist
     * toggle. "Play as Audio" appears for videos only.
     */
    private fun showTrackOptions(track: Track) {
        val inPlaylist = PlaylistStore.contains(this, track)
        val items = mutableListOf<String>()
        if (track.isVideo) items.add("Play as Audio")
        items.add("Select")
        items.add(if (inPlaylist) "Remove from Playlist" else "Add to Playlist")
        AlertDialog.Builder(this)
            .setTitle(track.title)
            .setItems(items.toTypedArray()) { _, which ->
                when (items[which]) {
                    "Play as Audio" -> {
                        val list = (listView.adapter as? TrackAdapter)?.items ?: return@setItems
                        val idx = list.indexOf(track)
                        if (idx >= 0) {
                            PlayerManager.playAsAudio(this, list, idx)
                            startActivity(Intent(this, PlayerActivity::class.java))
                        }
                    }
                    "Select" -> enterSelectMode(track)
                    "Add to Playlist", "Remove from Playlist" -> {
                        if (inPlaylist) PlaylistStore.remove(this, track)
                        else PlaylistStore.add(this, track)
                        Toast.makeText(
                            this,
                            if (inPlaylist) "Removed from Playlist" else "Added to Playlist",
                            Toast.LENGTH_SHORT
                        ).show()
                        if (mode == Mode.PLAYLIST) {
                            detailTracks =
                                PlaylistStore.resolve(this, allTracks)
                            refreshList()
                        }
                    }
                }
            }
            .show()
    }

    private fun enterSelectMode(track: Track) {
        selectMode = true
        selection.add(track.key)
        selectBackCallback.isEnabled = true
        syncAdapterSelection()
        updateSelectBar()
    }

    private fun toggleSelection(track: Track) {
        if (!selection.remove(track.key)) selection.add(track.key)
        if (selection.isEmpty()) {
            exitSelectMode()
            return
        }
        syncAdapterSelection()
        updateSelectBar()
    }

    private fun exitSelectMode() {
        selectMode = false
        selection.clear()
        selectBackCallback.isEnabled = false
        syncAdapterSelection()
        updateSelectBar()
    }

    private fun syncAdapterSelection() {
        (listView.adapter as? TrackAdapter)?.let {
            it.selectMode = selectMode
            it.selection = selection
            it.notifyDataSetChanged()
        }
    }

    private fun updateSelectBar() {
        val show = selectMode && selection.isNotEmpty()
        selectBar.visibility = if (show) View.VISIBLE else View.GONE
        if (show) {
            // v1.1.1: inside the Playlist the bar removes, not adds.
            selectActionButton.text = if (mode == Mode.PLAYLIST) {
                "Remove from Playlist (${selection.size})"
            } else {
                "Add to Playlist (${selection.size})"
            }
        }
    }

    private fun addSelectionToPlaylist() {
        val selected = selection.mapNotNull { key -> allTracks.find { it.key == key } }
        val added = PlaylistStore.addAll(this, selected)
        Toast.makeText(this, "$added added to Playlist", Toast.LENGTH_SHORT).show()
        if (mode == Mode.PLAYLIST) {
            detailTracks = PlaylistStore.resolve(this, allTracks)
        }
        exitSelectMode()
        refreshList()
    }

    /** v1.1.1: remove the selected tracks from the Playlist (only offered
     *  while viewing the Playlist — see updateSelectBar). */
    private fun removeSelectionFromPlaylist() {
        val selected = selection.mapNotNull { key -> allTracks.find { it.key == key } }
        val removed = PlaylistStore.removeAll(this, selected)
        Toast.makeText(this, "$removed removed from Playlist", Toast.LENGTH_SHORT).show()
        detailTracks = PlaylistStore.resolve(this, allTracks)
        exitSelectMode()
        refreshList()
    }

    private fun refreshMiniPlayer() {
        val player = PlayerManager.player
        val track = PlayerManager.current
        // v1.1.0: the mini-player also shows for a video (background play
        // video) — tapping it returns to the full player mid-playback.
        if (player == null || track == null) {
            miniPlayer.visibility = View.GONE
            return
        }
        miniPlayer.visibility = View.VISIBLE
        miniTitle.text = track.title
        miniPlayPause.setImageResource(
            if (player.isPlaying) R.drawable.ic_pause else R.drawable.ic_play
        )
    }
}
