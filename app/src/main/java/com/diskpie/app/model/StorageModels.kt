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
