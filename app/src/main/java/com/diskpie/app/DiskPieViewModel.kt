package com.diskpie.app

import android.app.Application
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.os.storage.StorageManager
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.diskpie.app.data.AppStorageReader
import com.diskpie.app.data.DeviceStorageReader
import com.diskpie.app.data.StorageFileManager
import com.diskpie.app.data.StorageScanner
import com.diskpie.app.model.AppStorageSummary
import com.diskpie.app.model.DeviceStorage
import com.diskpie.app.model.ScanProgress
import com.diskpie.app.model.ScanResult
import com.diskpie.app.model.StorageNode
import com.diskpie.app.model.StorageTree
import java.io.File
import java.util.concurrent.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SelectedFolder(
    val uri: String,
    val name: String,
    val isSelected: Boolean,
    val canWrite: Boolean,
    val isSharedStorageRoot: Boolean = false,
)

data class DiskPieUiState(
    val selectedFolders: List<SelectedFolder> = emptyList(),
    val supportsAllFilesAccess: Boolean = false,
    val hasAllFilesAccess: Boolean = false,
    val hasUsageStatsAccess: Boolean = false,
    val deviceStorage: DeviceStorage? = null,
    val appStorage: AppStorageSummary? = null,
    val isLoadingAppStorage: Boolean = false,
    val appStorageError: String? = null,
    val isScanning: Boolean = false,
    val progress: ScanProgress = ScanProgress(),
    val result: ScanResult? = null,
    val canDeleteFiles: Boolean = false,
    val isDeleting: Boolean = false,
    val errorMessage: String? = null,
    val actionMessage: String? = null,
)

class DiskPieViewModel(application: Application) : AndroidViewModel(application) {
    private val scanner = StorageScanner(application)
    private val fileManager = StorageFileManager(application)
    private val appStorageReader = AppStorageReader(application)
    private val preferences = application.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val initialAllFilesAccess = isAllFilesAccessGranted()
    private val initialUsageStatsAccess = appStorageReader.hasUsageStatsAccess()
    private val initialFolders = loadSavedFolders().let { savedFolders ->
        val withoutStaleRoots = if (initialAllFilesAccess) {
            savedFolders
        } else {
            savedFolders.filterNot(SelectedFolder::isSharedStorageRoot)
        }
        val rootsWereAlreadySaved = withoutStaleRoots.any(SelectedFolder::isSharedStorageRoot)
        val baseFolders = if (initialAllFilesAccess && !rootsWereAlreadySaved) {
            withoutStaleRoots.map { it.copy(isSelected = false) }
        } else {
            withoutStaleRoots
        }
        FolderSelectionRules.normalize(reconcileSharedStorageRoots(
            folders = baseFolders,
            accessGranted = initialAllFilesAccess,
            selectNewRoots = true,
        ))
    }
    private val _uiState = MutableStateFlow(
        DiskPieUiState(
            selectedFolders = initialFolders,
            supportsAllFilesAccess = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R,
            hasAllFilesAccess = initialAllFilesAccess,
            hasUsageStatsAccess = initialUsageStatsAccess,
            canDeleteFiles = canDeleteFromAllSelectedFolders(initialFolders),
        ),
    )
    val uiState: StateFlow<DiskPieUiState> = _uiState.asStateFlow()

    private var scanJob: Job? = null
    private var appStorageJob: Job? = null

    init {
        persistFolders(initialFolders)
        refreshDeviceStorage()
        if (initialUsageStatsAccess) refreshAppStorage()
        activeFolderUris(initialFolders).takeIf(List<String>::isNotEmpty)?.let(::startScan)
    }

    fun selectTree(uri: Uri) {
        val context = getApplication<Application>()
        val resolver = context.contentResolver
        var writePermissionGranted = true
        try {
            resolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        } catch (_: SecurityException) {
            writePermissionGranted = false
            try {
                resolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            } catch (_: SecurityException) {
                _uiState.update {
                    it.copy(errorMessage = "이 폴더의 읽기 권한을 유지할 수 없습니다. 다른 폴더를 선택해 주세요.")
                }
                return
            }
        }

        val uriString = uri.toString()
        val canWrite = writePermissionGranted && hasPersistedWritePermission(uriString)
        val name = resolveFolderName(uriString)
        val currentFolders = _uiState.value.selectedFolders.map { folder ->
            if (folder.isSharedStorageRoot) folder.copy(isSelected = false) else folder
        }
        val existingIndex = currentFolders.indexOfFirst { it.uri == uriString }
        val selectedFolder = SelectedFolder(
            uri = uriString,
            name = name,
            isSelected = true,
            canWrite = canWrite,
        )
        val updatedFolders = if (existingIndex >= 0) {
            currentFolders.toMutableList().apply { this[existingIndex] = selectedFolder }
        } else {
            currentFolders + selectedFolder
        }

        persistFolders(updatedFolders)
        _uiState.update {
            it.copy(
                selectedFolders = updatedFolders,
                result = null,
                canDeleteFiles = canDeleteFromAllSelectedFolders(updatedFolders),
                errorMessage = null,
                actionMessage = when {
                    !canWrite -> "‘$name’ 폴더를 읽기 전용으로 추가했습니다."
                    existingIndex >= 0 -> "‘$name’ 폴더를 다시 선택했습니다."
                    else -> "‘$name’ 폴더를 분석 목록에 추가했습니다."
                },
            )
        }
        startScan(activeFolderUris(updatedFolders))
    }

    fun setFolderSelected(uri: String, selected: Boolean) {
        val currentFolders = _uiState.value.selectedFolders
        val updatedFolders = FolderSelectionRules.toggle(currentFolders, uri, selected)
        if (updatedFolders == currentFolders) return
        applyFolderSelection(
            folders = updatedFolders,
            actionMessage = if (selected) "폴더를 분석 대상에 포함했습니다." else "폴더를 분석 대상에서 제외했습니다.",
        )
    }

    fun selectAllFolders() {
        val currentFolders = _uiState.value.selectedFolders
        if (currentFolders.isEmpty()) return
        val sharedRoots = currentFolders.filter(SelectedFolder::isSharedStorageRoot)
        val updatedFolders = FolderSelectionRules.selectAll(currentFolders)
        if (updatedFolders == currentFolders) return
        applyFolderSelection(
            folders = updatedFolders,
            actionMessage = if (sharedRoots.isNotEmpty()) {
                "전체 공유 저장공간을 선택했습니다."
            } else {
                "모든 폴더를 선택했습니다."
            },
        )
    }

    fun deselectAllFolders() {
        val currentFolders = _uiState.value.selectedFolders
        if (currentFolders.none(SelectedFolder::isSelected)) return
        applyFolderSelection(
            folders = currentFolders.map { it.copy(isSelected = false) },
            actionMessage = "모든 폴더 선택을 해제했습니다.",
        )
    }

    fun removeFolder(uri: String) {
        val currentFolders = _uiState.value.selectedFolders
        val removedFolder = currentFolders.firstOrNull { it.uri == uri } ?: return
        if (removedFolder.isSharedStorageRoot) {
            _uiState.update {
                it.copy(actionMessage = "전체 저장공간 항목은 시스템 권한 설정에서 관리해 주세요.")
            }
            return
        }
        val updatedFolders = currentFolders.filterNot { it.uri == uri }
        scanJob?.cancel()
        releasePersistedPermission(removedFolder)
        applyFolderSelection(
            folders = updatedFolders,
            actionMessage = "‘${removedFolder.name}’ 폴더를 목록에서 제거했습니다.",
        )
    }

    fun rescan() {
        activeFolderUris(_uiState.value.selectedFolders).takeIf(List<String>::isNotEmpty)?.let(::startScan)
    }

    fun cancelScan() {
        scanJob?.cancel()
        scanJob = null
        _uiState.update { it.copy(isScanning = false) }
    }

    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun dismissActionMessage() {
        _uiState.update { it.copy(actionMessage = null) }
    }

    fun deleteFile(file: StorageNode) {
        val state = _uiState.value
        val validationError = when {
            file.isDirectory -> "폴더 전체 삭제는 지원하지 않습니다. 개별 파일을 선택해 주세요."
            state.isScanning -> "분석이 끝난 뒤 파일을 삭제해 주세요."
            state.isDeleting -> "다른 파일을 삭제하고 있습니다. 잠시 기다려 주세요."
            !state.canDeleteFiles -> "선택한 모든 폴더에 삭제 권한이 있어야 합니다. 읽기 전용 폴더를 다시 선택해 주세요."
            state.result?.let { !StorageTree.containsFile(it.root, file.uri) } != false -> {
                "현재 분석 결과에 없는 파일입니다. 다시 분석한 뒤 시도해 주세요."
            }
            else -> null
        }
        if (validationError != null) {
            _uiState.update { it.copy(errorMessage = validationError) }
            return
        }

        _uiState.update { it.copy(isDeleting = true, errorMessage = null, actionMessage = null) }
        viewModelScope.launch {
            try {
                val deleted = fileManager.deleteFile(file.uri.toUri())
                if (!deleted) error("저장소 공급자가 삭제 요청을 거부했습니다.")

                _uiState.update {
                    it.copy(
                        isDeleting = false,
                        actionMessage = "‘${file.name}’ 파일을 삭제했습니다.",
                    )
                }
                activeFolderUris(_uiState.value.selectedFolders)
                    .takeIf(List<String>::isNotEmpty)
                    ?.let(::startScan)
            } catch (_: SecurityException) {
                _uiState.update {
                    it.copy(
                        isDeleting = false,
                        canDeleteFiles = false,
                        errorMessage = "삭제 권한이 없거나 만료되었습니다. 해당 폴더를 다시 선택해 주세요.",
                    )
                }
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(
                        isDeleting = false,
                        errorMessage = error.message ?: "파일을 삭제하지 못했습니다.",
                    )
                }
            }
        }
    }

    fun refreshDeviceStorage() {
        _uiState.update {
            it.copy(deviceStorage = runCatching(DeviceStorageReader::readInternalStorage).getOrNull())
        }
    }

    fun refreshAppStorageAccess() {
        val accessGranted = appStorageReader.hasUsageStatsAccess()
        val currentState = _uiState.value
        if (!accessGranted) {
            appStorageJob?.cancel()
            appStorageJob = null
            if (
                currentState.hasUsageStatsAccess ||
                currentState.appStorage != null ||
                currentState.isLoadingAppStorage
            ) {
                _uiState.update {
                    it.copy(
                        hasUsageStatsAccess = false,
                        appStorage = null,
                        isLoadingAppStorage = false,
                        appStorageError = null,
                    )
                }
            }
            return
        }

        val shouldRefresh = !currentState.hasUsageStatsAccess ||
            (currentState.appStorage == null && !currentState.isLoadingAppStorage)
        _uiState.update {
            it.copy(hasUsageStatsAccess = true, appStorageError = null)
        }
        if (shouldRefresh) refreshAppStorage()
    }

    fun refreshAppStorage() {
        if (!appStorageReader.hasUsageStatsAccess()) {
            _uiState.update {
                it.copy(
                    hasUsageStatsAccess = false,
                    appStorage = null,
                    isLoadingAppStorage = false,
                    appStorageError = "앱별 용량을 보려면 사용정보 접근을 허용해 주세요.",
                )
            }
            return
        }

        appStorageJob?.cancel()
        _uiState.update {
            it.copy(
                hasUsageStatsAccess = true,
                isLoadingAppStorage = true,
                appStorageError = null,
            )
        }
        appStorageJob = viewModelScope.launch {
            try {
                val summary = appStorageReader.read()
                _uiState.update {
                    it.copy(
                        appStorage = summary,
                        isLoadingAppStorage = false,
                        appStorageError = null,
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: SecurityException) {
                _uiState.update {
                    it.copy(
                        hasUsageStatsAccess = false,
                        appStorage = null,
                        isLoadingAppStorage = false,
                        appStorageError = "앱 사용정보 접근 권한이 해제되었습니다.",
                    )
                }
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(
                        isLoadingAppStorage = false,
                        appStorageError = error.message ?: "앱별 저장공간을 불러오지 못했습니다.",
                    )
                }
            }
        }
    }

    fun refreshAllFilesAccess() {
        val accessGranted = isAllFilesAccessGranted()
        val currentState = _uiState.value
        val baseFolders = when {
            accessGranted && !currentState.hasAllFilesAccess -> {
                currentState.selectedFolders.map { folder ->
                    if (folder.isSharedStorageRoot) folder else folder.copy(isSelected = false)
                }
            }
            !accessGranted -> currentState.selectedFolders.filterNot(SelectedFolder::isSharedStorageRoot)
            else -> currentState.selectedFolders
        }
        val shouldSelectNewRoots = accessGranted && (
            !currentState.hasAllFilesAccess ||
                currentState.selectedFolders.any {
                    it.isSharedStorageRoot && it.isSelected
                }
            )
        val updatedFolders = FolderSelectionRules.normalize(reconcileSharedStorageRoots(
            folders = baseFolders,
            accessGranted = accessGranted,
            selectNewRoots = shouldSelectNewRoots,
        ))
        if (
            accessGranted == currentState.hasAllFilesAccess &&
            updatedFolders == currentState.selectedFolders
        ) {
            return
        }

        val message = when {
            accessGranted && !currentState.hasAllFilesAccess -> {
                "모든 파일 접근이 허용되어 공유 저장공간 전체를 분석합니다."
            }
            !accessGranted && currentState.hasAllFilesAccess -> {
                "모든 파일 접근이 해제되어 직접 저장공간 분석을 중지했습니다."
            }
            else -> "연결된 저장장치 목록을 갱신했습니다."
        }
        applyFolderSelection(
            folders = updatedFolders,
            actionMessage = message,
            hasAllFilesAccess = accessGranted,
        )
    }

    private fun applyFolderSelection(
        folders: List<SelectedFolder>,
        actionMessage: String,
        hasAllFilesAccess: Boolean = _uiState.value.hasAllFilesAccess,
    ) {
        persistFolders(folders)
        val activeUris = activeFolderUris(folders)
        _uiState.update {
            it.copy(
                selectedFolders = folders,
                hasAllFilesAccess = hasAllFilesAccess,
                result = null,
                canDeleteFiles = canDeleteFromAllSelectedFolders(folders),
                errorMessage = null,
                actionMessage = actionMessage,
            )
        }

        if (activeUris.isEmpty()) {
            scanJob?.cancel()
            scanJob = null
            _uiState.update {
                it.copy(isScanning = false, progress = ScanProgress(), result = null)
            }
        } else {
            startScan(activeUris)
        }
    }

    private fun startScan(uriStrings: List<String>) {
        val distinctUris = uriStrings.distinct()
        if (distinctUris.isEmpty()) return

        scanJob?.cancel()
        _uiState.update {
            it.copy(
                isScanning = true,
                progress = ScanProgress(),
                errorMessage = null,
            )
        }

        scanJob = viewModelScope.launch {
            try {
                val result = scanner.scan(distinctUris.map(String::toUri)) { progress ->
                    _uiState.update { current -> current.copy(progress = progress) }
                }
                _uiState.update {
                    it.copy(
                        isScanning = false,
                        progress = ScanProgress(),
                        result = result,
                    )
                }
                refreshDeviceStorage()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: SecurityException) {
                _uiState.update {
                    it.copy(
                        isScanning = false,
                        canDeleteFiles = false,
                        errorMessage = "선택한 폴더 중 접근 권한이 만료된 항목이 있습니다. 해당 폴더를 다시 선택해 주세요.",
                    )
                }
            } catch (error: Exception) {
                _uiState.update {
                    it.copy(
                        isScanning = false,
                        errorMessage = error.message ?: "저장공간을 분석하는 중 문제가 발생했습니다.",
                    )
                }
            }
        }
    }

    private fun loadSavedFolders(): List<SelectedFolder> {
        val savedUris = preferences.getString(KEY_TREE_URIS, null)
            ?.lineSequence()
            ?.filter(String::isNotBlank)
            ?.distinct()
            ?.toList()
            ?: listOfNotNull(preferences.getString(KEY_LEGACY_TREE_URI, null))
        val savedActiveUris = preferences.getString(KEY_ACTIVE_TREE_URIS, null)
            ?.lineSequence()
            ?.filter(String::isNotBlank)
            ?.toSet()
            ?: savedUris.toSet()

        return savedUris.map { uriString ->
            val isSharedStorageRoot = uriString.toUri().scheme == ContentResolver.SCHEME_FILE
            SelectedFolder(
                uri = uriString,
                name = resolveFolderName(uriString),
                isSelected = uriString in savedActiveUris &&
                    (!isSharedStorageRoot || initialAllFilesAccess),
                canWrite = if (isSharedStorageRoot) {
                    initialAllFilesAccess
                } else {
                    hasPersistedWritePermission(uriString)
                },
                isSharedStorageRoot = isSharedStorageRoot,
            )
        }
    }

    private fun persistFolders(folders: List<SelectedFolder>) {
        preferences.edit {
            putString(KEY_TREE_URIS, folders.joinToString("\n", transform = SelectedFolder::uri))
            putString(
                KEY_ACTIVE_TREE_URIS,
                folders.filter(SelectedFolder::isSelected)
                    .joinToString("\n", transform = SelectedFolder::uri),
            )
            remove(KEY_LEGACY_TREE_URI)
        }
    }

    private fun resolveFolderName(uriString: String): String {
        val uri = uriString.toUri()
        if (uri.scheme == ContentResolver.SCHEME_FILE) {
            return uri.path?.let(::File)?.name?.takeIf(String::isNotBlank)
                ?: "공유 저장공간"
        }
        val providerName = runCatching {
            DocumentFile.fromTreeUri(getApplication(), uri)?.name
        }.getOrNull()?.takeIf(String::isNotBlank)
        if (providerName != null) return providerName

        return runCatching {
            DocumentsContract.getTreeDocumentId(uri)
                .substringAfterLast('/')
                .substringAfterLast(':')
                .takeIf(String::isNotBlank)
        }.getOrNull() ?: "선택한 폴더"
    }

    private fun releasePersistedPermission(folder: SelectedFolder) {
        if (folder.isSharedStorageRoot) return
        val resolver = getApplication<Application>().contentResolver
        val persistedPermission = resolver.persistedUriPermissions.firstOrNull {
            it.uri.toString() == folder.uri
        } ?: return
        var flags = 0
        if (persistedPermission.isReadPermission) flags = flags or Intent.FLAG_GRANT_READ_URI_PERMISSION
        if (persistedPermission.isWritePermission) flags = flags or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        if (flags == 0) return
        runCatching {
            resolver.releasePersistableUriPermission(folder.uri.toUri(), flags)
        }
    }

    private fun hasPersistedWritePermission(uriString: String): Boolean =
        getApplication<Application>().contentResolver.persistedUriPermissions.any { permission ->
            permission.uri.toString() == uriString && permission.isWritePermission
        }

    private fun isAllFilesAccessGranted(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()

    private fun reconcileSharedStorageRoots(
        folders: List<SelectedFolder>,
        accessGranted: Boolean,
        selectNewRoots: Boolean,
    ): List<SelectedFolder> {
        val customFolders = folders.filterNot(SelectedFolder::isSharedStorageRoot)
        if (!accessGranted || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return customFolders

        val existingRoots = folders
            .filter(SelectedFolder::isSharedStorageRoot)
            .associateBy(SelectedFolder::uri)
        val context = getApplication<Application>()
        val storageManager = context.getSystemService(StorageManager::class.java)
        val sharedRoots = storageManager.storageVolumes.mapNotNull { volume ->
            val directory = volume.directory ?: return@mapNotNull null
            if (!directory.exists() || !directory.isDirectory) return@mapNotNull null
            val uriString = directory.toUri().toString()
            val existing = existingRoots[uriString]
            SelectedFolder(
                uri = uriString,
                name = volume.getDescription(context),
                isSelected = existing?.isSelected ?: selectNewRoots,
                canWrite = true,
                isSharedStorageRoot = true,
            )
        }.distinctBy(SelectedFolder::uri)

        return sharedRoots + customFolders
    }

    private fun activeFolderUris(folders: List<SelectedFolder>): List<String> =
        folders.filter(SelectedFolder::isSelected).map(SelectedFolder::uri)

    private fun canDeleteFromAllSelectedFolders(folders: List<SelectedFolder>): Boolean {
        val selectedFolders = folders.filter(SelectedFolder::isSelected)
        return selectedFolders.isNotEmpty() && selectedFolders.all(SelectedFolder::canWrite)
    }

    private companion object {
        const val PREFERENCES_NAME = "diskpie_preferences"
        const val KEY_TREE_URIS = "selected_tree_uris"
        const val KEY_ACTIVE_TREE_URIS = "active_tree_uris"
        const val KEY_LEGACY_TREE_URI = "selected_tree_uri"
    }
}
