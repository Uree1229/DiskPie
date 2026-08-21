package com.diskpie.app.data

import android.os.Environment
import android.os.StatFs
import com.diskpie.app.model.DeviceStorage

object DeviceStorageReader {
    fun readInternalStorage(): DeviceStorage {
        val stats = StatFs(Environment.getDataDirectory().absolutePath)
        return DeviceStorage(
            totalBytes = stats.totalBytes.coerceAtLeast(0L),
            availableBytes = stats.availableBytes.coerceAtLeast(0L),
        )
    }
}
