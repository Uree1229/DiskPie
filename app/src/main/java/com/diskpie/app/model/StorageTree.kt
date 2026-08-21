package com.diskpie.app.model

object StorageTree {
    fun combineRoots(roots: List<StorageNode>): StorageNode {
        require(roots.isNotEmpty()) { "At least one storage root is required." }
        if (roots.size == 1) return roots.single()

        val totalBytes = roots.fold(0L) { total, root -> saturatedAdd(total, root.sizeBytes) }
        val dominantCategory = roots
            .groupingBy(StorageNode::category)
            .fold(0L) { total, root -> saturatedAdd(total, root.sizeBytes) }
            .maxByOrNull { it.value }
            ?.key
            ?: FileCategory.OTHER
        return StorageNode(
            uri = COMBINED_ROOT_URI,
            name = "선택한 폴더 ${roots.size}개",
            sizeBytes = totalBytes,
            isDirectory = true,
            mimeType = null,
            category = dominantCategory,
            relativePath = "선택한 폴더",
            children = roots.sortedByDescending(StorageNode::sizeBytes),
        )
    }

    fun containsFile(root: StorageNode, uri: String): Boolean {
        val pending = ArrayDeque<StorageNode>()
        pending.addLast(root)
        while (pending.isNotEmpty()) {
            val node = pending.removeLast()
            if (!node.isDirectory && node.uri == uri) return true
            node.children.forEach(pending::addLast)
        }
        return false
    }

    fun searchFiles(
        root: StorageNode,
        query: String,
        limit: Int = 100,
    ): List<StorageNode> {
        if (limit <= 0) return emptyList()
        val normalizedQuery = query.trim()
        if (normalizedQuery.isEmpty()) return emptyList()

        val matches = ArrayList<StorageNode>(limit)
        val pending = ArrayDeque<StorageNode>()
        root.children.asReversed().forEach(pending::addLast)
        while (pending.isNotEmpty() && matches.size < limit) {
            val node = pending.removeLast()
            if (node.isDirectory) {
                node.children.asReversed().forEach(pending::addLast)
            } else if (node.name.contains(normalizedQuery, ignoreCase = true)) {
                matches += node
            }
        }
        return matches.sortedByDescending(StorageNode::sizeBytes)
    }

    private fun saturatedAdd(left: Long, right: Long): Long =
        if (Long.MAX_VALUE - left < right) Long.MAX_VALUE else left + right

    private const val COMBINED_ROOT_URI = "diskpie://selected-folders"
}
