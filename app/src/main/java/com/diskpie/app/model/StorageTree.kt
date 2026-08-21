package com.diskpie.app.model

object StorageTree {
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
}
