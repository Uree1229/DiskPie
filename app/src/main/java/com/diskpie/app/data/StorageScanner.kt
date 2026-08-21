package com.diskpie.app.data

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.diskpie.app.model.CategoryUsage
import com.diskpie.app.model.FileCategory
import com.diskpie.app.model.FileClassifier
import com.diskpie.app.model.ScanProgress
import com.diskpie.app.model.ScanResult
import com.diskpie.app.model.StorageNode
import java.util.PriorityQueue
import kotlin.coroutines.coroutineContext
import kotlin.time.TimeSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

class StorageScanner(private val context: Context) {
    suspend fun scan(
        treeUri: Uri,
        onProgress: (ScanProgress) -> Unit,
    ): ScanResult = withContext(Dispatchers.IO) {
        val rootDocument = DocumentFile.fromTreeUri(context, treeUri)
            ?: error("선택한 폴더를 열 수 없습니다.")
        if (!rootDocument.exists() || !rootDocument.isDirectory) {
            error("선택한 위치가 존재하지 않거나 폴더가 아닙니다.")
        }

        val startedAt = TimeSource.Monotonic.markNow()
        val accumulator = ScanAccumulator(onProgress)
        val root = accumulator.scanNode(rootDocument, parentPath = null)
        val categories = FileCategory.entries.mapNotNull { category ->
            val size = accumulator.categoryBytes[category] ?: 0L
            val count = accumulator.categoryCounts[category] ?: 0
            if (size == 0L && count == 0) null else CategoryUsage(category, size, count)
        }.sortedByDescending(CategoryUsage::sizeBytes)

        ScanResult(
            root = root,
            totalBytes = root.sizeBytes,
            fileCount = accumulator.fileCount,
            directoryCount = accumulator.directoryCount,
            inaccessibleCount = accumulator.inaccessibleCount,
            elapsedMillis = startedAt.elapsedNow().inWholeMilliseconds,
            categories = categories,
            largestFiles = accumulator.largestFiles.sortedByDescending(StorageNode::sizeBytes),
        )
    }

    private class ScanAccumulator(
        private val onProgress: (ScanProgress) -> Unit,
    ) {
        val categoryBytes = mutableMapOf<FileCategory, Long>()
        val categoryCounts = mutableMapOf<FileCategory, Int>()
        var fileCount = 0
            private set
        var directoryCount = 0
            private set
        var inaccessibleCount = 0
            private set

        private var bytesSeen = 0L
        private var itemsSinceProgress = 0
        private val visitedUris = mutableSetOf<String>()
        private val largestFileHeap = PriorityQueue<StorageNode>(
            compareBy(StorageNode::sizeBytes),
        )

        val largestFiles: List<StorageNode>
            get() = largestFileHeap.toList()

        suspend fun scanNode(document: DocumentFile, parentPath: String?): StorageNode {
            coroutineContext.ensureActive()
            val uriString = document.uri.toString()
            val name = try {
                document.name?.takeIf(String::isNotBlank) ?: "이름 없는 항목"
            } catch (_: RuntimeException) {
                inaccessibleCount++
                "접근할 수 없는 항목"
            }
            val relativePath = if (parentPath == null) name else "$parentPath/$name"

            if (!visitedUris.add(uriString)) {
                inaccessibleCount++
                return emptyDirectory(uriString, name, relativePath)
            }

            val isDirectory = try {
                document.isDirectory
            } catch (_: RuntimeException) {
                inaccessibleCount++
                return emptyDirectory(uriString, name, relativePath)
            }

            return if (isDirectory) {
                scanDirectory(document, uriString, name, relativePath)
            } else {
                scanFile(document, uriString, name, relativePath)
            }
        }

        private suspend fun scanDirectory(
            document: DocumentFile,
            uriString: String,
            name: String,
            relativePath: String,
        ): StorageNode {
            directoryCount++
            reportProgress(name)

            val rawChildren = try {
                document.listFiles().asList()
            } catch (_: SecurityException) {
                inaccessibleCount++
                emptyList()
            } catch (_: RuntimeException) {
                inaccessibleCount++
                emptyList()
            }

            val children = ArrayList<StorageNode>(rawChildren.size)
            var totalSize = 0L
            val categorySizes = mutableMapOf<FileCategory, Long>()
            for (child in rawChildren) {
                coroutineContext.ensureActive()
                val node = scanNode(child, relativePath)
                children += node
                totalSize = saturatedAdd(totalSize, node.sizeBytes)
                categorySizes[node.category] = saturatedAdd(
                    categorySizes[node.category] ?: 0L,
                    node.sizeBytes,
                )
            }

            return StorageNode(
                uri = uriString,
                name = name,
                sizeBytes = totalSize,
                isDirectory = true,
                mimeType = null,
                category = categorySizes.maxByOrNull { it.value }?.key ?: FileCategory.OTHER,
                relativePath = relativePath,
                children = children.sortedByDescending(StorageNode::sizeBytes),
            )
        }

        private fun scanFile(
            document: DocumentFile,
            uriString: String,
            name: String,
            relativePath: String,
        ): StorageNode {
            val size = try {
                document.length().coerceAtLeast(0L)
            } catch (_: RuntimeException) {
                inaccessibleCount++
                0L
            }
            val mimeType = try {
                document.type
            } catch (_: RuntimeException) {
                inaccessibleCount++
                null
            }
            val category = FileClassifier.classify(name, mimeType)
            val node = StorageNode(
                uri = uriString,
                name = name,
                sizeBytes = size,
                isDirectory = false,
                mimeType = mimeType,
                category = category,
                relativePath = relativePath,
            )

            fileCount++
            bytesSeen = saturatedAdd(bytesSeen, size)
            categoryBytes[category] = saturatedAdd(categoryBytes[category] ?: 0L, size)
            categoryCounts[category] = (categoryCounts[category] ?: 0) + 1
            rememberLargeFile(node)
            reportProgress(name)
            return node
        }

        private fun rememberLargeFile(node: StorageNode) {
            largestFileHeap += node
            if (largestFileHeap.size > LARGEST_FILE_LIMIT) {
                largestFileHeap.poll()
            }
        }

        private fun reportProgress(currentName: String) {
            itemsSinceProgress++
            if (itemsSinceProgress < PROGRESS_INTERVAL) return
            itemsSinceProgress = 0
            onProgress(
                ScanProgress(
                    fileCount = fileCount,
                    directoryCount = directoryCount,
                    bytesSeen = bytesSeen,
                    currentName = currentName,
                ),
            )
        }

        private fun emptyDirectory(uri: String, name: String, relativePath: String) = StorageNode(
            uri = uri,
            name = name,
            sizeBytes = 0L,
            isDirectory = true,
            mimeType = null,
            category = FileCategory.OTHER,
            relativePath = relativePath,
        )

        private fun saturatedAdd(left: Long, right: Long): Long =
            if (Long.MAX_VALUE - left < right) Long.MAX_VALUE else left + right

        private companion object {
            const val LARGEST_FILE_LIMIT = 20
            const val PROGRESS_INTERVAL = 32
        }
    }
}
