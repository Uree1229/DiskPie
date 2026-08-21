package com.diskpie.app.model

data class StorageNode(
    val uri: String,
    val name: String,
    val sizeBytes: Long,
    val isDirectory: Boolean,
    val mimeType: String?,
    val category: FileCategory,
    val relativePath: String = name,
    val children: List<StorageNode> = emptyList(),
)

data class CategoryUsage(
    val category: FileCategory,
    val sizeBytes: Long,
    val fileCount: Int,
)

data class ScanProgress(
    val fileCount: Int = 0,
    val directoryCount: Int = 0,
    val bytesSeen: Long = 0L,
    val currentName: String = "",
)

data class ScanResult(
    val root: StorageNode,
    val totalBytes: Long,
    val fileCount: Int,
    val directoryCount: Int,
    val inaccessibleCount: Int,
    val elapsedMillis: Long,
    val categories: List<CategoryUsage>,
    val largestFiles: List<StorageNode>,
)

data class DeviceStorage(
    val totalBytes: Long,
    val availableBytes: Long,
) {
    val usedBytes: Long = (totalBytes - availableBytes).coerceAtLeast(0L)
    val usedFraction: Float = if (totalBytes > 0L) {
        (usedBytes.toDouble() / totalBytes.toDouble()).toFloat().coerceIn(0f, 1f)
    } else {
        0f
    }
}

data class AppStorageUsage(
    val packageName: String,
    val label: String,
    val codeBytes: Long,
    val dataBytes: Long,
    val cacheBytes: Long,
    val isSystemApp: Boolean,
) {
    val totalBytes: Long = safeAdd(safeAdd(codeBytes, dataBytes), cacheBytes)

    companion object {
        fun fromPlatformStats(
            packageName: String,
            label: String,
            codeBytes: Long,
            dataBytesIncludingCache: Long,
            cacheBytes: Long,
            isSystemApp: Boolean,
        ): AppStorageUsage {
            val normalizedData = dataBytesIncludingCache.coerceAtLeast(0L)
            val normalizedCache = cacheBytes.coerceIn(0L, normalizedData)
            return AppStorageUsage(
                packageName = packageName,
                label = label,
                codeBytes = codeBytes.coerceAtLeast(0L),
                dataBytes = normalizedData - normalizedCache,
                cacheBytes = normalizedCache,
                isSystemApp = isSystemApp,
            )
        }
    }
}

data class AppStorageSummary(
    val apps: List<AppStorageUsage>,
    val totalBytes: Long,
    val inaccessibleAppCount: Int,
    val elapsedMillis: Long,
)

private fun safeAdd(left: Long, right: Long): Long =
    if (right > 0L && Long.MAX_VALUE - left < right) Long.MAX_VALUE else left + right
