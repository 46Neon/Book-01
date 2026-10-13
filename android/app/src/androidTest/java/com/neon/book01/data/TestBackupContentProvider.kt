package com.neon.book01.data

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileNotFoundException

/** Exported only by the androidTest APK to exercise ContentResolver URI streams. */
class TestBackupContentProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun getType(uri: Uri): String = "application/vnd.book01.encrypted-backup"

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int =
        if (backupFile(uri).delete()) 1 else 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int = 0

    // Android invokes this across the test APK's provider boundary. Nullable platform
    // parameters avoid Kotlin's generated Intrinsics call in the provider process.
    override fun openFile(uri: Uri?, mode: String?): ParcelFileDescriptor {
        val safeUri = uri ?: throw FileNotFoundException("Missing test document URI")
        val safeMode = mode ?: throw FileNotFoundException("Missing test document mode")
        val file = backupFile(safeUri)
        file.parentFile?.mkdirs()
        val flags = when (safeMode) {
            "r" -> ParcelFileDescriptor.MODE_READ_ONLY
            "w" -> ParcelFileDescriptor.MODE_WRITE_ONLY or
                ParcelFileDescriptor.MODE_CREATE or ParcelFileDescriptor.MODE_TRUNCATE
            else -> throw FileNotFoundException("Unsupported test mode")
        }
        return ParcelFileDescriptor.open(file, flags)
    }

    private fun backupFile(uri: Uri?): File {
        val safeUri = uri ?: throw FileNotFoundException("Missing test document URI")
        val name = safeUri.lastPathSegment ?: throw FileNotFoundException("Missing test document name")
        if (!name.matches(Regex("[A-Za-z0-9._-]{1,80}"))) {
            throw FileNotFoundException("Invalid test document name")
        }
        val base = context?.cacheDir ?: throw FileNotFoundException("Test provider has no context")
        return File(base, "backup-document-$name")
    }
}
