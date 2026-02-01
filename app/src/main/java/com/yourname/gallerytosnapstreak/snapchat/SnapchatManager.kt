package com.yourname.gallerytosnapstreak.snapchat

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
// Snapchat Creative Kit removed for this build; using Intent fallback only.
import java.io.File

object SnapchatManager {
    private const val TAG = "SnapchatManager"

    // Try using Creative Kit if available; otherwise fallback to Intent sharing
    fun sendFiles(context: Context, files: List<File>, packageName: String = "com.snapchat.android") {
        // Intent-only sharing (Creative Kit removed)
        try {
            val uris = ArrayList<Uri>()
            for (f in files) {
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", f)
                uris.add(uri)
            }
            val intent = Intent().apply {
                action = Intent.ACTION_SEND_MULTIPLE
                type = "*/*"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                `package` = packageName
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Intent fallback failed", e)
        }
    }
}
