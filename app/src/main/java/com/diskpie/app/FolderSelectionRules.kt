package com.diskpie.app

internal object FolderSelectionRules {
    fun toggle(
        folders: List<SelectedFolder>,
        uri: String,
        selected: Boolean,
    ): List<SelectedFolder> {
        val targetFolder = folders.firstOrNull { it.uri == uri } ?: return folders
        return folders.map { folder ->
            when {
                folder.uri == uri -> folder.copy(isSelected = selected)
                selected && targetFolder.isSharedStorageRoot && !folder.isSharedStorageRoot -> {
                    folder.copy(isSelected = false)
                }
                selected && !targetFolder.isSharedStorageRoot && folder.isSharedStorageRoot -> {
                    folder.copy(isSelected = false)
                }
                else -> folder
            }
        }
    }

    fun selectAll(folders: List<SelectedFolder>): List<SelectedFolder> {
        val hasSharedStorageRoots = folders.any(SelectedFolder::isSharedStorageRoot)
        return if (hasSharedStorageRoots) {
            folders.map { folder ->
                folder.copy(isSelected = folder.isSharedStorageRoot)
            }
        } else {
            folders.map { it.copy(isSelected = true) }
        }
    }

    fun normalize(folders: List<SelectedFolder>): List<SelectedFolder> {
        if (folders.none { it.isSharedStorageRoot && it.isSelected }) return folders
        return folders.map { folder ->
            if (folder.isSharedStorageRoot) folder else folder.copy(isSelected = false)
        }
    }
}
