package com.diskpie.app.data

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class StorageFileManager(context: Context) {
    private val contentResolver = context.contentResolver

    suspend fun deleteFile(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        if (uri.scheme == ContentResolver.SCHEME_FILE) {
            uri.path?.let(::File)?.delete() == true
        } else {
            DocumentsContract.deleteDocument(contentResolver, uri)
        }
    }
}
