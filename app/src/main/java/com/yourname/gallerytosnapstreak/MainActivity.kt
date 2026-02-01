package com.yourname.gallerytosnapstreak

import android.Manifest
import android.app.Activity
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import android.text.Editable
import androidx.core.widget.doAfterTextChanged
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

data class MediaItem(val uri: Uri, val mime: String, val dateTaken: Long)

class MainActivity : AppCompatActivity() {
    private val TAG = "MainActivity"

    private lateinit var grid: RecyclerView
    private lateinit var streakText: TextView
    private lateinit var sendFab: View
    private lateinit var processingOverlay: View
    private lateinit var progressBar: ProgressBar
    private lateinit var progressStatus: TextView
    private lateinit var cancelButton: Button

    private val scope = CoroutineScope(Dispatchers.Main + Job())

    // full media list and filtered list
    private val items = mutableListOf<MediaItem>()
    private val filtered = mutableListOf<MediaItem>()
    private val selected = mutableSetOf<Uri>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        grid = findViewById(R.id.grid)
        streakText = findViewById(R.id.streakCounter)
        sendFab = findViewById(R.id.sendFab)
        processingOverlay = findViewById(R.id.processingOverlay)
        progressBar = findViewById(R.id.progressBar)
        progressStatus = findViewById(R.id.progressStatus)
        cancelButton = findViewById(R.id.cancelButton)

        grid.layoutManager = GridLayoutManager(this, 3)
        grid.adapter = GalleryAdapter(this, filtered, selected)

        findViewById<Button>(R.id.filterAll).setOnClickListener { applyFilter("all") }
        findViewById<Button>(R.id.filterPhotos).setOnClickListener { applyFilter("image/") }
        findViewById<Button>(R.id.filterVideos).setOnClickListener { applyFilter("video/") }
        findViewById<Button>(R.id.filterAudio).setOnClickListener { applyFilter("audio/") }
        findViewById<EditText>(R.id.searchBox).doAfterTextChanged { s: Editable? ->
            val q = s?.toString() ?: ""
            applySearch(q)
        }

        sendFab.setOnClickListener { if (selected.isNotEmpty()) processAndSend() else Toast.makeText(this, "Select media first", Toast.LENGTH_SHORT).show() }

        cancelButton.setOnClickListener { processingOverlay.visibility = View.GONE }

        checkPermissionsAndLoad()
        loadStreak()
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    private fun checkPermissionsAndLoad() {
        val perms = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms += Manifest.permission.READ_MEDIA_IMAGES
            perms += Manifest.permission.READ_MEDIA_VIDEO
            perms += Manifest.permission.READ_MEDIA_AUDIO
        } else {
            perms += Manifest.permission.READ_EXTERNAL_STORAGE
        }

        val missing = perms.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), 3001)
        } else {
            loadMedia()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 3001) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                loadMedia()
            } else {
                Toast.makeText(this, "Permissions required", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun loadMedia() {
        scope.launch {
            val loaded = withContext(Dispatchers.IO) { queryMedia() }
            items.clear()
            items.addAll(loaded)
            applyFilter("all")
        }
    }

    private fun queryMedia(): List<MediaItem> {
        val list = mutableListOf<MediaItem>()
        try {
            val projection = arrayOf(MediaStore.Files.FileColumns._ID, MediaStore.Files.FileColumns.MEDIA_TYPE, MediaStore.MediaColumns.MIME_TYPE, MediaStore.Images.Media.DATE_TAKEN)
            val uri = MediaStore.Files.getContentUri("external")
            val selection = ("${MediaStore.Files.FileColumns.MEDIA_TYPE}=${MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE} OR ${MediaStore.Files.FileColumns.MEDIA_TYPE}=${MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO} OR ${MediaStore.Files.FileColumns.MEDIA_TYPE}=${MediaStore.Files.FileColumns.MEDIA_TYPE_AUDIO}")
            val sort = "${MediaStore.MediaColumns.DATE_ADDED} DESC"

            contentResolver.query(uri, null, selection, null, sort)?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                val mimeCol = cursor.getColumnIndex(MediaStore.MediaColumns.MIME_TYPE)
                val dateCol = cursor.getColumnIndex(MediaStore.Images.Media.DATE_TAKEN)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val mime = if (mimeCol >= 0) cursor.getString(mimeCol) ?: "" else ""
                    val date = if (dateCol >= 0) cursor.getLong(dateCol) else 0L
                    val contentUri = ContentUris.withAppendedId(MediaStore.Files.getContentUri("external"), id)
                    list.add(MediaItem(contentUri, mime, date))
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "queryMedia failed", e)
        }
        return list
    }

    private fun applyFilter(filter: String) {
        filtered.clear()
        if (filter == "all") filtered.addAll(items) else filtered.addAll(items.filter { it.mime.startsWith(filter) })
        grid.adapter?.notifyDataSetChanged()
    }

    private fun applySearch(q: String) {
        if (q.isBlank()) {
            applyFilter("all")
            return
        }
        filtered.clear()
        filtered.addAll(items.filter { it.uri.toString().contains(q, ignoreCase = true) || (it.mime?.contains(q, true) == true) })
        grid.adapter?.notifyDataSetChanged()
    }

    private fun processAndSend() {
        processingOverlay.visibility = View.VISIBLE
        progressBar.progress = 0
        progressStatus.text = "Preparing media..."

        scope.launch {
            try {
                val uris = selected.toList()
                val prepared = withContext(Dispatchers.IO) { prepareFilesForSharing(uris) }
                progressBar.progress = 80
                progressStatus.text = "Launching Snapchat..."
                withContext(Dispatchers.Main) { sendToSnapchat(prepared) }
            } catch (e: Exception) {
                Log.e(TAG, "processing failed", e)
                Toast.makeText(this@MainActivity, "Processing failed: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                processingOverlay.visibility = View.GONE
            }
        }
    }

    private fun prepareFilesForSharing(uris: List<Uri>): List<File> {
        val outFiles = mutableListOf<File>()
        val sdf = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US)
        val now = sdf.format(Date())

        uris.forEachIndexed { idx, uri ->
            val mime = contentResolver.getType(uri) ?: "application/octet-stream"
            val ext = when {
                mime.startsWith("image/") -> ".jpg"
                mime.startsWith("video/") -> ".mp4"
                mime.startsWith("audio/") -> ".mp3"
                else -> ""
            }
            val tmp = File(cacheDir, "gts_${System.currentTimeMillis()}_$idx$ext")
            contentResolver.openInputStream(uri).use { ins ->
                FileOutputStream(tmp).use { out ->
                    ins?.copyTo(out)
                }
            }

            // Images: update EXIF
            if (mime.startsWith("image/")) {
                try {
                    val exif = ExifInterface(tmp.absolutePath)
                    exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, now)
                    exif.setAttribute(ExifInterface.TAG_MODEL, "GalleryToSnapStreak")
                    exif.saveAttributes()
                } catch (e: Exception) {
                    Log.w(TAG, "exif update failed", e)
                }
                outFiles.add(tmp)
            } else if (mime.startsWith("video/")) {
                // For this build we skip FFmpeg transcoding and use the copied file directly.
                outFiles.add(tmp)
            } else {
                // audio or fallback
                outFiles.add(tmp)
            }
        }
        return outFiles
    }

    private fun sendToSnapchat(files: List<File>) {
        val snapPackage = "com.snapchat.android"
        // Use SnapchatManager which prefers Creative Kit and falls back to Intent
        try {
            com.yourname.gallerytosnapstreak.snapchat.SnapchatManager.sendFiles(this, files, snapPackage)
        } catch (e: Exception) {
            Toast.makeText(this, "Unable to open Snapchat: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 4001) {
            incrementStreak()
            Toast.makeText(this, "Returned from Snapchat", Toast.LENGTH_SHORT).show()
            // schedule cleanup of cache files
            cleanupCache()
        }
    }

    private fun cleanupCache() {
        scope.launch(Dispatchers.IO) {
            cacheDir.listFiles()?.forEach { f ->
                try { if (f.name.startsWith("gts_")) f.delete() } catch (_: Exception) {}
            }
        }
    }

    private fun loadStreak() {
        val prefs = getSharedPreferences("streaks", Context.MODE_PRIVATE)
        val count = prefs.getInt("count", 0)
        streakText.text = "Streak Counter: 🔥 $count"
    }

    private fun incrementStreak() {
        val prefs = getSharedPreferences("streaks", Context.MODE_PRIVATE)
        val lastTs = prefs.getLong("lastTs", 0L)
        val now = System.currentTimeMillis()
        val diff = now - lastTs
        val editor = prefs.edit()
        var count = prefs.getInt("count", 0)
        if (lastTs == 0L || diff <= 24L * 60L * 60L * 1000L) count += 1 else count = 1
        editor.putInt("count", count)
        editor.putLong("lastTs", now)
        editor.apply()
        streakText.text = "Streak Counter: 🔥 $count"
    }

    // RecyclerView adapter
    private class GalleryAdapter(val ctx: Context, val items: List<MediaItem>, val selected: MutableSet<Uri>) : RecyclerView.Adapter<GalleryAdapter.VH>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.gallery_item, parent, false)
            return VH(v)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = items[position]
            holder.thumb.load(item.uri) { placeholder(android.R.drawable.ic_menu_gallery) }
            holder.type.text = when {
                item.mime.startsWith("image/") -> "IMG"
                item.mime.startsWith("video/") -> "VID"
                item.mime.startsWith("audio/") -> "AUD"
                else -> "FILE"
            }
            holder.check.isChecked = selected.contains(item.uri)
            holder.itemView.setOnClickListener {
                if (selected.contains(item.uri)) selected.remove(item.uri) else selected.add(item.uri)
                holder.check.isChecked = selected.contains(item.uri)
            }
            holder.check.setOnClickListener {
                if (holder.check.isChecked) selected.add(item.uri) else selected.remove(item.uri)
            }
        }

        override fun getItemCount(): Int = items.size

        class VH(view: View) : RecyclerView.ViewHolder(view) {
            val thumb: ImageView = view.findViewById(R.id.thumbnail)
            val check: CheckBox = view.findViewById(R.id.check)
            val type: TextView = view.findViewById(R.id.typeLabel)
        }
    }
}
