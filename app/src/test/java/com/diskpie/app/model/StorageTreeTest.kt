package com.diskpie.app.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StorageTreeTest {
    private val photo = file("content://tree/photo", "Summer.JPG", 300)
    private val note = file("content://tree/note", "meeting-note.txt", 20)
    private val nested = directory("content://tree/nested", "nested", listOf(note))
    private val root = directory("content://tree", "root", listOf(photo, nested))

    @Test
    fun `containsFile accepts scanned files but not folders or unknown uris`() {
        assertTrue(StorageTree.containsFile(root, photo.uri))
        assertTrue(StorageTree.containsFile(root, note.uri))
        assertFalse(StorageTree.containsFile(root, nested.uri))
        assertFalse(StorageTree.containsFile(root, "content://outside/file"))
    }

    @Test
    fun `searchFiles is case insensitive and searches nested folders`() {
        assertEquals(listOf(photo), StorageTree.searchFiles(root, "summer"))
        assertEquals(listOf(note), StorageTree.searchFiles(root, "NOTE"))
    }

    @Test
    fun `searchFiles respects result limit`() {
        val manyFiles = (1..5).map { file("content://tree/$it", "item-$it.txt", it.toLong()) }
        val manyRoot = directory("content://many", "many", manyFiles)

        assertEquals(2, StorageTree.searchFiles(manyRoot, "item", limit = 2).size)
        assertTrue(StorageTree.searchFiles(manyRoot, "item", limit = 0).isEmpty())
    }

    @Test
    fun `combineRoots creates one searchable result without losing folders`() {
        val secondFile = file("content://second/file", "archive.zip", 700)
        val secondRoot = directory("content://second", "second", listOf(secondFile))

        val combined = StorageTree.combineRoots(listOf(root, secondRoot))

        assertEquals("선택한 폴더 2개", combined.name)
        assertEquals(1_020L, combined.sizeBytes)
        assertEquals(listOf(secondRoot, root), combined.children)
        assertTrue(StorageTree.containsFile(combined, photo.uri))
        assertEquals(listOf(secondFile), StorageTree.searchFiles(combined, "archive"))
    }

    @Test
    fun `combineRoots keeps a single root unchanged`() {
        assertEquals(root, StorageTree.combineRoots(listOf(root)))
    }

    private fun file(uri: String, name: String, size: Long) = StorageNode(
        uri = uri,
        name = name,
        sizeBytes = size,
        isDirectory = false,
        mimeType = "text/plain",
        category = FileCategory.DOCUMENT,
    )

    private fun directory(uri: String, name: String, children: List<StorageNode>) = StorageNode(
        uri = uri,
        name = name,
        sizeBytes = children.sumOf(StorageNode::sizeBytes),
        isDirectory = true,
        mimeType = null,
        category = FileCategory.OTHER,
        children = children,
    )
}
