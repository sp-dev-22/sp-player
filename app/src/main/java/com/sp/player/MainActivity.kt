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
import com.sp.player.ui.TrackAdapter

class MainActivity : AppCompatActivity() {

    companion object {
        private const val REQ_MEDIA = 101
    }

    private enum class Mode { MUSIC, VIDEO, FOLDERS, FOLDER_DETAIL, PLAYLIST }

    private lateinit var headerTitle: TextView
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

    private var mode = Mode.MUSIC
    private var audioTracks: List<Track> = emptyList()
    private var videoTracks: List<Track> = emptyList()
    private var folders: List<Folder> = emptyList()
    private var detailTracks: List<Track> = emptyList()
    private var detailTitle: String = ""
    private var query: String = ""

    private val handler = Handler(Looper.getMainLooper())
    private val miniPlayerUpdater = object : Runnable {
        override fun run() {
            refreshMiniPlayer()
            handler.postDelayed(this, 500)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        applyNightMode()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        headerTitle = findViewById(R.id.header_title)
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

        refreshList()
    }

    override fun onResume() {
        super.onResume()
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
                    "अनुमति के बिना लाइब्रेरी नहीं दिखेगी",
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
            if (Prefs.sortBy(this) == Prefs.SORT_DATE) "तारीख: नई पहले" else "नाम: A-Z"
        headerTitle.text = when (mode) {
            Mode.FOLDER_DETAIL, Mode.PLAYLIST -> detailTitle
            else -> getString(R.string.app_name)
        }
        when (mode) {
            Mode.MUSIC -> {
                emptyText.text = "कोई गाना नहीं मिला"
                showTracks(filtered(audioTracks))
            }
            Mode.VIDEO -> {
                emptyText.text = "कोई वीडियो नहीं मिला"
                showTracks(filtered(videoTracks))
            }
            Mode.FOLDERS -> {
                emptyText.text = "कोई फ़ोल्डर नहीं मिला"
                showFolders()
            }
            Mode.FOLDER_DETAIL -> {
                emptyText.text = "इस फ़ोल्डर में कुछ नहीं है"
                showTracks(filtered(detailTracks))
            }
            Mode.PLAYLIST -> {
                emptyText.text = "Playlist खाली है — किसी गाने/वीडियो को देर तक दबाकर जोड़ें"
                showTracks(filtered(detailTracks))
            }
        }
    }

    private fun highlightTab(tab: TextView, selected: Boolean, accent: Int, secondary: Int) {
        tab.setTextColor(if (selected) accent else secondary)
        tab.setTypeface(null, if (selected) Typeface.BOLD else Typeface.NORMAL)
    }

    private fun applyAccent() {
        val accent = Prefs.accent(this)
        miniPlayer.setBackgroundColor(accent)
        sortButton.setTextColor(accent)
        settingsButton.setTextColor(accent)
        miniPlayPause.imageTintList = ColorStateList.valueOf(
            ContextCompat.getColor(this, android.R.color.white)
        )
        miniNext.imageTintList = ColorStateList.valueOf(
            ContextCompat.getColor(this, android.R.color.white)
        )
    }

    private fun showTracks(tracks: List<Track>) {
        emptyText.visibility = if (tracks.isEmpty()) View.VISIBLE else View.GONE
        listView.adapter = TrackAdapter(this, tracks)
        listView.onItemClickListener =
            AdapterView.OnItemClickListener { _, _, position, _ ->
                val list = (listView.adapter as TrackAdapter).items
                val track = list[position]
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
                showTrackOptions(list[position])
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
                    detailTracks = PlaylistStore.resolve(this, audioTracks + videoTracks)
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

    private fun showTrackOptions(track: Track) {
        val inPlaylist = PlaylistStore.contains(this, track)
        val options = arrayOf(
            "चलाएं",
            if (inPlaylist) "Playlist से हटाएं" else "Playlist में जोड़ें"
        )
        AlertDialog.Builder(this)
            .setTitle(track.title)
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        val list = (listView.adapter as? TrackAdapter)?.items ?: return@setItems
                        val idx = list.indexOf(track)
                        if (idx >= 0) {
                            playFromList(list, idx)
                            if (track.isVideo) {
                                startActivity(Intent(this, PlayerActivity::class.java))
                            } else {
                                refreshMiniPlayer()
                            }
                        }
                    }
                    1 -> {
                        if (inPlaylist) PlaylistStore.remove(this, track)
                        else PlaylistStore.add(this, track)
                        Toast.makeText(
                            this,
                            if (inPlaylist) "Playlist से हटाया गया" else "Playlist में जोड़ा गया",
                            Toast.LENGTH_SHORT
                        ).show()
                        if (mode == Mode.PLAYLIST) {
                            detailTracks =
                                PlaylistStore.resolve(this, audioTracks + videoTracks)
                            refreshList()
                        }
                    }
                }
            }
            .show()
    }

    private fun refreshMiniPlayer() {
        val player = PlayerManager.player
        val track = PlayerManager.current
        if (player == null || track == null || track.isVideo) {
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
