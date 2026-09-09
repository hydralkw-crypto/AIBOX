package com.aibox.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

/**
 * قسم My Videos: اختيار فيديو من الجهاز، تشغيله، حذفه، والبحث فيه.
 * لا يحتاج أي حساب TikTok أو Spotify.
 */
class MyVideosActivity : AppCompatActivity() {

    private lateinit var listView: ListView
    private lateinit var videoPlayer: VideoView
    private lateinit var adapter: ArrayAdapter<String>
    private val allVideos = mutableListOf<Uri>()
    private val displayNames = mutableListOf<String>()

    private val prefsName = "aibox_my_videos"

    private val pickVideoLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { addVideo(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_my_videos)

        listView = findViewById(R.id.videoListView)
        videoPlayer = findViewById(R.id.videoPlayer)
        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, displayNames)
        listView.adapter = adapter

        loadSavedVideos()

        findViewById<Button>(R.id.btnAddVideo).setOnClickListener {
            pickVideoLauncher.launch(arrayOf("video/*"))
        }

        listView.setOnItemClickListener { _, _, position, _ ->
            playVideo(allVideos[position])
        }

        listView.setOnItemLongClickListener { _, _, position, _ ->
            deleteVideo(position)
            true
        }

        findViewById<EditText>(R.id.searchBox).addTextChangedListener(
            object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {
                    filterVideos(s?.toString().orEmpty())
                }
                override fun afterTextChanged(s: android.text.Editable?) {}
            }
        )
    }

    private fun addVideo(uri: Uri) {
        contentResolver.takePersistableUriPermission(
            uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
        )
        allVideos.add(uri)
        displayNames.add(uri.lastPathSegment ?: "video")
        adapter.notifyDataSetChanged()
        persistVideos()
    }

    private fun deleteVideo(position: Int) {
        allVideos.removeAt(position)
        displayNames.removeAt(position)
        adapter.notifyDataSetChanged()
        persistVideos()
        videoPlayer.visibility = View.GONE
    }

    private fun playVideo(uri: Uri) {
        videoPlayer.visibility = View.VISIBLE
        videoPlayer.setVideoURI(uri)
        videoPlayer.setOnPreparedListener { it.start() }
    }

    private fun filterVideos(query: String) {
        adapter.filter.filter(query)
    }

    private fun persistVideos() {
        val prefs = getSharedPreferences(prefsName, MODE_PRIVATE)
        prefs.edit().putStringSet(
            "uris", allVideos.map { it.toString() }.toSet()
        ).apply()
    }

    private fun loadSavedVideos() {
        val prefs = getSharedPreferences(prefsName, MODE_PRIVATE)
        val saved = prefs.getStringSet("uris", emptySet()) ?: emptySet()
        for (s in saved) {
            val uri = Uri.parse(s)
            allVideos.add(uri)
            displayNames.add(uri.lastPathSegment ?: "video")
        }
        adapter.notifyDataSetChanged()
    }

    override fun onStop() {
        super.onStop()
        // نوقف ونحرر موارد الفيديو عند مغادرة الشاشة لتوفير الموارد
        if (videoPlayer.isPlaying) videoPlayer.stopPlayback()
    }
}
