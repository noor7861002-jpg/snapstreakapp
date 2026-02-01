package com.yourname.gallerytosnapstreak.snapchat

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import com.snapchat.kit.sdk.SnapCreative
import com.snapchat.kit.sdk.creative.SnapMediaFactory
import com.snapchat.kit.sdk.creative.SnapPhotoContent
import com.snapchat.kit.sdk.creative.SnapVideoContent
import java.io.File

object SnapchatManager {
    private const val TAG = "SnapchatManager"

    // Try using Creative Kit if available; otherwise fallback to Intent sharing
    fun sendFiles(context: Context, files: List<File>, packageName: String = "com.snapchat.android") {
        try {
            // Try Creative Kit
            val api = SnapCreative.getApi(context)
            val factory: SnapMediaFactory = api.mediaFactory
            // If single file, try photo or video content
            if (files.size == 1) {
                val f = files[0]
                if (f.extension.equals("mp4", true) || f.extension.equals("mov", true)) {
                    val video = factory.getSnapVideoFromFile(f)
                    val content = SnapVideoContent(video)
                    api.send(content)
                    return
                } else {
                    val photo = factory.getSnapPhotoFromFile(f)
                    val content = SnapPhotoContent(photo)
                    api.send(content)
                    return
                }
            }

            // Multiple files: fallback to Intent sharing
            Log.i(TAG, "Creative Kit: multiple files or unsupported. Using Intent fallback.")
        } catch (e: Exception) {
            Log.w(TAG, "Creative Kit unavailable or failed, falling back to Intent", e)
        }

        // Intent fallback
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
