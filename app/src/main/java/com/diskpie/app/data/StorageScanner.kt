package com.diskpie.app.data

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.webkit.MimeTypeMap
import androidx.documentfile.provider.DocumentFile
import com.diskpie.app.model.CategoryUsage
import com.diskpie.app.model.FileCategory
import com.diskpie.app.model.FileClassifier
import com.diskpie.app.model.ScanProgress
import com.diskpie.app.model.ScanResult
import com.diskpie.app.model.StorageNode
import com.diskpie.app.model.StorageTree
import java.io.File
import java.io.IOException
import java.util.concurrent.CancellationException
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
    ): ScanResult = scan(listOf(treeUri), onProgress)

    suspend fun scan(
        treeUris: List<Uri>,
        onProgress: (ScanProgress) -> Unit,
    ): ScanResult = withContext(Dispatchers.IO) {
        require(treeUris.isNotEmpty()) { "분석할 폴더를 한 개 이상 선택해 주세요." }

        val startedAt = TimeSource.Monotonic.markNow()
        val accumulator = ScanAccumulator(onProgress)
        val roots = treeUris.distinctBy(Uri::toString).mapNotNull { treeUri ->
            coroutineContext.ensureActive()
            try {
                if (treeUri.scheme == ContentResolver.SCHEME_FILE) {
                    val rootPath = treeUri.path
                    val rootFile = rootPath?.let(::File)
                    if (rootFile == null || !rootFile.exists() || !rootFile.isDirectory) {
                        accumulator.markInaccessible()
                        return@mapNotNull null
                    }
                    accumulator.scanLocalNode(rootFile, parentPath = null)
                } else {
                    val rootDocument = DocumentFile.fromTreeUri(context, treeUri)
                        ?: run {
                            accumulator.markInaccessible()
                            return@mapNotNull null
                        }
                    if (!rootDocument.exists() || !rootDocument.isDirectory) {
                        accumulator.markInaccessible()
                        return@mapNotNull null
                    }
                    accumulator.scanNode(rootDocument, parentPath = null)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: SecurityException) {
                accumulator.markInaccessible()
                null
            } catch (_: RuntimeException) {
                accumulator.markInaccessible()
                null
            }
        }
        if (roots.isEmpty()) {
            error("선택한 폴더를 열 수 없습니다. 폴더 접근 권한을 다시 확인해 주세요.")
        }
        val root = StorageTree.combineRoots(roots)
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
        private val visitedDocuments = mutableSetOf<String>()
        private val largestFileHeap = PriorityQueue<StorageNode>(
            compareBy(StorageNode::sizeBytes),
        )

        val largestFiles: List<StorageNode>
            get() = largestFileHeap.toList()

        fun markInaccessible() {
            inaccessibleCount++
        }

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

            if (!visitedDocuments.add(documentKey(document.uri))) {
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

        suspend fun scanLocalNode(file: File, parentPath: String?): StorageNode {
            coroutineContext.ensureActive()
            val uriString = Uri.fromFile(file).toString()
            val name = file.name.takeIf(String::isNotBlank) ?: file.absolutePath
            val relativePath = if (parentPath == null) name else "$parentPath/$name"
            val canonicalPath = try {
                file.canonicalPath
            } catch (_: IOException) {
                inaccessibleCount++
                file.absolutePath
            } catch (_: RuntimeException) {
                inaccessibleCount++
                file.absolutePath
            }

            if (!visitedDocuments.add("file:$canonicalPath")) {
                inaccessibleCount++
                return emptyDirectory(uriString, name, relativePath)
            }

            return try {
                when {
                    file.isDirectory -> scanLocalDirectory(file, uriString, name, relativePath)
                    file.isFile -> scanLocalFile(file, uriString, name, relativePath)
                    else -> {
                        inaccessibleCount++
                        emptyDirectory(uriString, name, relativePath)
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: SecurityException) {
                inaccessibleCount++
                emptyDirectory(uriString, name, relativePath)
            } catch (_: RuntimeException) {
                inaccessibleCount++
                emptyDirectory(uriString, name, relativePath)
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

        private suspend fun scanLocalDirectory(
            file: File,
            uriString: String,
            name: String,
            relativePath: String,
        ): StorageNode {
            directoryCount++
            reportProgress(name)

            val rawChildren = try {
                file.listFiles()?.asList() ?: run {
                    inaccessibleCount++
                    emptyList()
                }
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
                val node = scanLocalNode(child, relativePath)
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

        private fun scanLocalFile(
            file: File,
            uriString: String,
            name: String,
            relativePath: String,
        ): StorageNode {
            val size = try {
                file.length().coerceAtLeast(0L)
            } catch (_: RuntimeException) {
                inaccessibleCount++
                0L
            }
            val extension = name.substringAfterLast('.', missingDelimiterValue = "")
                .lowercase()
            val mimeType = extension.takeIf(String::isNotBlank)?.let {
                MimeTypeMap.getSingleton().getMimeTypeFromExtension(it)
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

        private fun documentKey(uri: Uri): String = runCatching {
            "document:${uri.authority}:${DocumentsContract.getDocumentId(uri)}"
        }.getOrElse { uri.toString() }

        private fun saturatedAdd(left: Long, right: Long): Long =
            if (Long.MAX_VALUE - left < right) Long.MAX_VALUE else left + right

        private companion object {
            const val LARGEST_FILE_LIMIT = 20
            const val PROGRESS_INTERVAL = 32
        }
    }
}
