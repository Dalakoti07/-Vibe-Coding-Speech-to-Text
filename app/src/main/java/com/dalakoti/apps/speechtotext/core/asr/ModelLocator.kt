package com.dalakoti.apps.speechtotext.core.asr

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.Settings
import com.dalakoti.apps.speechtotext.core.model.StorageAccess
import java.io.File

/**
 * Answers "can I see the model root, and where is it".
 *
 * MANAGE_EXTERNAL_STORAGE is not a runtime permission: requestPermissions() does nothing
 * for it, it is granted from a Settings page, and there is no callback. Without the grant
 * every path still resolves and File.exists() quietly returns false — which looks exactly
 * like a bad sherpa config. So we check it explicitly before touching any model file.
 */
object ModelLocator {

    const val DEFAULT_ROOT = "/sdcard/Models"

    fun storageAccess(): StorageAccess =
        if (Environment.isExternalStorageManager()) StorageAccess.Granted
        else StorageAccess.NeedsAllFilesAccess

    fun hasAccess(): Boolean = Environment.isExternalStorageManager()

    /**
     * Intent that takes the user to the grant page. Some OEM builds do not resolve the
     * per-app page, so fall back to the global list before giving up.
     */
    fun grantIntent(context: Context): Intent? {
        val perApp = Intent(
            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
            Uri.parse("package:${context.packageName}"),
        )
        if (perApp.resolveActivity(context.packageManager) != null) return perApp

        val global = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
        if (global.resolveActivity(context.packageManager) != null) return global

        return null
    }

    /**
     * Derive a real filesystem path from an ACTION_OPEN_DOCUMENT_TREE result.
     * The picker is convenience; the path is what the JNI layer opens.
     * Returns null for non-primary volumes — say so rather than guessing.
     */
    fun treeUriToPath(uri: Uri): File? {
        val docId = runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull()
            ?: return null
        val parts = docId.split(":", limit = 2)
        val volume = parts.getOrNull(0) ?: return null
        val relative = parts.getOrNull(1).orEmpty()
        if (!volume.equals("primary", ignoreCase = true)) return null
        return File(Environment.getExternalStorageDirectory(), relative)
    }
}
