package com.diskpie.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FolderSelectionRulesTest {
    private val internalRoot = folder("file:///storage/emulated/0", sharedRoot = true)
    private val sdRoot = folder("file:///storage/1234-5678", sharedRoot = true)
    private val pictures = folder("content://documents/pictures")
    private val downloads = folder("content://documents/downloads")

    @Test
    fun `selecting a custom folder switches away from shared storage roots`() {
        val updated = FolderSelectionRules.toggle(
            listOf(internalRoot, sdRoot, pictures),
            uri = pictures.uri,
            selected = true,
        )

        assertFalse(updated.first { it.uri == internalRoot.uri }.isSelected)
        assertFalse(updated.first { it.uri == sdRoot.uri }.isSelected)
        assertTrue(updated.first { it.uri == pictures.uri }.isSelected)
    }

    @Test
    fun `selecting a shared root switches away from custom folders`() {
        val updated = FolderSelectionRules.toggle(
            listOf(
                internalRoot.copy(isSelected = false),
                pictures.copy(isSelected = true),
                downloads.copy(isSelected = true),
            ),
            uri = internalRoot.uri,
            selected = true,
        )

        assertTrue(updated.first { it.uri == internalRoot.uri }.isSelected)
        assertFalse(updated.first { it.uri == pictures.uri }.isSelected)
        assertFalse(updated.first { it.uri == downloads.uri }.isSelected)
    }

    @Test
    fun `select all prefers every shared root without duplicate custom scans`() {
        val updated = FolderSelectionRules.selectAll(
            listOf(
                internalRoot.copy(isSelected = false),
                sdRoot.copy(isSelected = false),
                pictures.copy(isSelected = true),
            ),
        )

        assertTrue(updated.filter(SelectedFolder::isSharedStorageRoot).all(SelectedFolder::isSelected))
        assertFalse(updated.first { it.uri == pictures.uri }.isSelected)
    }

    @Test
    fun `select all selects every custom folder when broad roots are unavailable`() {
        val updated = FolderSelectionRules.selectAll(
            listOf(
                pictures.copy(isSelected = false),
                downloads.copy(isSelected = false),
            ),
        )

        assertTrue(updated.all(SelectedFolder::isSelected))
    }

    private fun folder(uri: String, sharedRoot: Boolean = false) = SelectedFolder(
        uri = uri,
        name = uri.substringAfterLast('/'),
        isSelected = sharedRoot,
        canWrite = true,
        isSharedStorageRoot = sharedRoot,
    )
}
