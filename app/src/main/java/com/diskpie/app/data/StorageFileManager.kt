package com.diskpie.app.data

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class StorageFileManager(context: Context) {
    private val contentResolver = context.contentResolver

    suspend fun deleteFile(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        DocumentsContract.deleteDocument(contentResolver, uri)
    }
}
