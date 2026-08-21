package com.diskpie.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.diskpie.app.DiskPieUiState
import com.diskpie.app.SelectedFolder
import com.diskpie.app.model.CategoryUsage
import com.diskpie.app.model.DeviceStorage
import com.diskpie.app.model.FileCategory
import com.diskpie.app.model.ScanProgress
import com.diskpie.app.model.ScanResult
import com.diskpie.app.model.StorageNode
import com.diskpie.app.model.StorageTree
import com.diskpie.app.ui.theme.Cream
import com.diskpie.app.ui.theme.Leaf
import com.diskpie.app.ui.theme.Muted
import com.diskpie.app.ui.theme.Outline
import com.diskpie.app.ui.theme.RaisedInk
import com.diskpie.app.ui.theme.SoftInk
import com.diskpie.app.ui.theme.Warning
import com.diskpie.app.ui.treemap.StorageTreemap
import com.diskpie.app.ui.treemap.TreemapEntry
import com.diskpie.app.ui.treemap.categoryColor
import com.diskpie.app.util.ByteFormatter
import java.util.Locale

@Composable
fun DiskPieScreen(
    uiState: DiskPieUiState,
    onChooseFolder: () -> Unit,
    onRequestAllFilesAccess: () -> Unit,
    onToggleFolder: (String, Boolean) -> Unit,
    onSelectAllFolders: () -> Unit,
    onDeselectAllFolders: () -> Unit,
    onRemoveFolder: (String) -> Unit,
    onRescan: () -> Unit,
    onCancelScan: () -> Unit,
    onDismissError: () -> Unit,
    onDismissActionMessage: () -> Unit,
    onOpenFile: (StorageNode) -> Unit,
    onDeleteFile: (StorageNode) -> Unit,
) {
    var searchQuery by remember(uiState.result) { mutableStateOf("") }
    var filePendingDeletion by remember { mutableStateOf<StorageNode?>(null) }
    var showAllFilesDisclosure by remember { mutableStateOf(false) }
    val displayedFiles: List<StorageNode> = remember(uiState.result, searchQuery) {
        val result = uiState.result ?: return@remember emptyList()
        if (searchQuery.isBlank()) {
            result.largestFiles
        } else {
            StorageTree.searchFiles(result.root, searchQuery, FILE_SEARCH_LIMIT)
        }
    }
    val deletionEnabled = uiState.canDeleteFiles && !uiState.isScanning && !uiState.isDeleting
    val selectedFolderCount = uiState.selectedFolders.count(SelectedFolder::isSelected)

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 20.dp,
                top = 18.dp,
                end = 20.dp,
                bottom = 40.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { AppHeader() }

            uiState.deviceStorage?.let { storage ->
                item { DeviceStorageCard(storage) }
            }

            if (uiState.supportsAllFilesAccess) {
                item {
                    AllFilesAccessCard(
                        hasAccess = uiState.hasAllFilesAccess,
                        onRequestAccess = {
                            if (uiState.hasAllFilesAccess) {
                                onRequestAllFilesAccess()
                            } else {
                                showAllFilesDisclosure = true
                            }
                        },
                    )
                }
            }

            uiState.errorMessage?.let { message ->
                item { ErrorCard(message, onDismissError) }
            }

            uiState.actionMessage?.let { message ->
                item { ActionMessageCard(message, onDismissActionMessage) }
            }

            if (uiState.selectedFolders.isEmpty()) {
                item { EmptyState(onChooseFolder) }
            } else {
                item {
                    FolderSelectionCard(
                        folders = uiState.selectedFolders,
                        onChooseFolder = onChooseFolder,
                        onToggleFolder = onToggleFolder,
                        onSelectAllFolders = onSelectAllFolders,
                        onDeselectAllFolders = onDeselectAllFolders,
                        onRemoveFolder = onRemoveFolder,
                    )
                }
                if (selectedFolderCount == 0) {
                    item { NoFolderSelectedCard() }
                }
            }

            if (uiState.isScanning) {
                item { ScanningCard(uiState.progress, onCancelScan) }
            }

            if (uiState.isDeleting) {
                item { DeletingCard() }
            }

            uiState.result?.let { result ->
                item {
                    SelectionSummaryCard(
                        result = result,
                        isScanning = uiState.isScanning,
                        onRescan = onRescan,
                        onChooseFolder = onChooseFolder,
                    )
                }
                item {
                    TreemapSection(
                        result = result,
                        deletionEnabled = deletionEnabled,
                        onRequestDelete = { filePendingDeletion = it },
                    )
                }
                item { CategorySection(result.categories, result.totalBytes) }
                item {
                    FileSearchSection(
                        query = searchQuery,
                        onQueryChange = { searchQuery = it },
                        canDeleteFiles = uiState.canDeleteFiles,
                        onChooseFolder = onChooseFolder,
                    )
                }
                items(displayedFiles, key = StorageNode::uri) { file ->
                    FileRow(
                        file = file,
                        deletionEnabled = deletionEnabled,
                        onOpen = { onOpenFile(file) },
                        onDelete = { filePendingDeletion = file },
                    )
                }
                if (searchQuery.isNotBlank() && displayedFiles.isEmpty()) {
                    item {
                        Text("일치하는 파일이 없습니다.", color = Muted, fontSize = 13.sp)
                    }
                }
                if (result.inaccessibleCount > 0) {
                    item {
                        Text(
                            text = "접근할 수 없거나 중복된 항목 ${result.inaccessibleCount}개는 집계에서 제외했습니다.",
                            color = Muted,
                            fontSize = 12.sp,
                        )
                    }
                }
            }
        }
    }

    filePendingDeletion?.let { file ->
        DeleteFileDialog(
            file = file,
            onDismiss = { filePendingDeletion = null },
            onConfirm = {
                filePendingDeletion = null
                onDeleteFile(file)
            },
        )
    }

    if (showAllFilesDisclosure) {
        AllFilesAccessDisclosureDialog(
            onDismiss = { showAllFilesDisclosure = false },
            onAgree = {
                showAllFilesDisclosure = false
                onRequestAllFilesAccess()
            },
        )
    }
}

@Composable
private fun AppHeader() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(Leaf),
            contentAlignment = Alignment.Center,
        ) {
            Text("◔", color = Color(0xFF101311), fontSize = 29.sp, fontWeight = FontWeight.Black)
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                text = "DiskPie",
                color = Cream,
                fontSize = 24.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = (-0.5).sp,
            )
            Text("내 저장공간을 한눈에", color = Muted, fontSize = 13.sp)
        }
    }
}

@Composable
private fun DeviceStorageCard(storage: DeviceStorage) {
    DashboardCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { storage.usedFraction },
                    modifier = Modifier.size(92.dp),
                    color = Leaf,
                    trackColor = Outline,
                    strokeWidth = 9.dp,
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${(storage.usedFraction * 100).toInt()}%",
                        color = Cream,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text("사용", color = Muted, fontSize = 11.sp)
                }
            }
            Spacer(Modifier.width(20.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text("기기 저장공간", color = Muted, fontSize = 13.sp)
                Text(
                    ByteFormatter.format(storage.usedBytes),
                    color = Cream,
                    fontSize = 27.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "전체 ${ByteFormatter.format(storage.totalBytes)}  ·  여유 ${ByteFormatter.format(storage.availableBytes)}",
                    color = Muted,
                    fontSize = 12.sp,
                )
            }
        }
    }
}

@Composable
private fun AllFilesAccessCard(
    hasAccess: Boolean,
    onRequestAccess: () -> Unit,
) {
    DashboardCard(containerColor = if (hasAccess) Color(0xFF1D2A1C) else SoftInk) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionTitle(
                "전체 저장공간 탐색",
                if (hasAccess) "허용됨" else "추가 동의 필요",
            )
            Text(
                text = if (hasAccess) {
                    "공유 저장공간과 연결된 SD·USB의 접근 가능한 폴더를 한 번에 분석합니다."
                } else {
                    "Android의 앱별 ‘모든 파일 접근’을 허용하면 폴더를 하나씩 고르지 않고 공유 저장공간 전체를 분석할 수 있습니다."
                },
                color = Muted,
                fontSize = 13.sp,
                lineHeight = 19.sp,
            )
            if (hasAccess) {
                Text(
                    "다른 앱의 Android/data 전용 영역은 운영체제 정책상 계속 제외됩니다.",
                    color = Warning,
                    fontSize = 12.sp,
                )
            }
            Button(
                onClick = onRequestAccess,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Leaf,
                    contentColor = Color(0xFF101311),
                ),
            ) {
                Text(if (hasAccess) "권한 설정 열기" else "전체 접근 동의 및 설정", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun AllFilesAccessDisclosureDialog(
    onDismiss: () -> Unit,
    onAgree: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("전체 파일 접근 동의") },
        text = {
            Text(
                "DiskPie가 저장공간 사용량을 계산하고 파일 열기·삭제 기능을 제공하기 위해 공유 저장공간의 파일 이름, 크기와 폴더 구조를 기기 안에서 읽습니다. 분석 정보는 외부로 전송하지 않습니다. 동의 후 Android 시스템 설정에서도 이 앱의 ‘모든 파일 접근’을 허용해 주세요. 다른 앱의 보호된 Android/data 영역은 허용 후에도 접근할 수 없습니다.",
            )
        },
        confirmButton = {
            Button(onClick = onAgree) { Text("동의하고 설정 열기") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("취소") }
        },
    )
}

@Composable
private fun EmptyState(onChooseFolder: () -> Unit) {
    DashboardCard(containerColor = SoftInk) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("어디가 공간을 차지할까요?", color = Cream, fontSize = 21.sp, fontWeight = FontWeight.Bold)
            Text(
                "폴더를 목록에 여러 개 추가하면 선택한 모든 위치를 한 번에 분석합니다. 추가한 폴더는 개별 또는 전체 선택·해제할 수 있습니다.",
                color = Muted,
                fontSize = 14.sp,
                lineHeight = 21.sp,
            )
            Button(
                onClick = onChooseFolder,
                colors = ButtonDefaults.buttonColors(containerColor = Leaf, contentColor = Color(0xFF101311)),
            ) {
                Text("첫 폴더 선택", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun FolderSelectionCard(
    folders: List<SelectedFolder>,
    onChooseFolder: () -> Unit,
    onToggleFolder: (String, Boolean) -> Unit,
    onSelectAllFolders: () -> Unit,
    onDeselectAllFolders: () -> Unit,
    onRemoveFolder: (String) -> Unit,
) {
    val selectedCount = folders.count(SelectedFolder::isSelected)
    val sharedStorageRoots = folders.filter(SelectedFolder::isSharedStorageRoot)
    val allTargetFoldersSelected = if (sharedStorageRoots.isNotEmpty()) {
        sharedStorageRoots.all(SelectedFolder::isSelected) &&
            folders.filterNot(SelectedFolder::isSharedStorageRoot).none(SelectedFolder::isSelected)
    } else {
        folders.all(SelectedFolder::isSelected)
    }
    DashboardCard {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SectionTitle("분석 폴더", "${selectedCount}/${folders.size}개 선택")
                Spacer(Modifier.weight(1f))
                OutlinedButton(onClick = onChooseFolder) { Text("폴더 추가") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(
                    onClick = onSelectAllFolders,
                    enabled = !allTargetFoldersSelected,
                ) {
                    Text(if (sharedStorageRoots.isNotEmpty()) "전체 저장공간 선택" else "전체 폴더 선택")
                }
                TextButton(
                    onClick = onDeselectAllFolders,
                    enabled = selectedCount > 0,
                ) { Text("전체 폴더 해제") }
            }
            folders.forEach { folder ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = folder.isSelected,
                        onCheckedChange = { checked -> onToggleFolder(folder.uri, checked) },
                    )
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = folder.name,
                            color = Cream,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = when {
                                folder.isSharedStorageRoot -> "전체 접근 · 읽기·삭제 가능"
                                folder.canWrite -> "읽기·삭제 가능"
                                else -> "읽기 전용"
                            },
                            color = if (folder.canWrite) Muted else Warning,
                            fontSize = 11.sp,
                        )
                    }
                    if (folder.isSharedStorageRoot) {
                        Text("시스템", color = Muted, fontSize = 11.sp)
                    } else {
                        TextButton(onClick = { onRemoveFolder(folder.uri) }) { Text("제거") }
                    }
                }
            }
        }
    }
}

@Composable
private fun NoFolderSelectedCard() {
    DashboardCard(containerColor = SoftInk) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("분석할 폴더가 선택되지 않았습니다.", color = Cream, fontWeight = FontWeight.Bold)
            Text(
                "위 목록에서 폴더를 체크하거나 ‘전체 폴더 선택’을 눌러 주세요.",
                color = Muted,
                fontSize = 13.sp,
            )
        }
    }
}

@Composable
private fun ScanningCard(progress: ScanProgress, onCancel: () -> Unit) {
    DashboardCard(containerColor = Color(0xFF1D2A1C)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(
                modifier = Modifier.size(42.dp),
                color = Leaf,
                trackColor = Outline,
                strokeWidth = 4.dp,
            )
            Spacer(Modifier.width(15.dp))
            Column(Modifier.weight(1f)) {
                Text("저장공간 분석 중", color = Cream, fontWeight = FontWeight.Bold)
                Text(
                    "파일 ${formatCount(progress.fileCount)}개  ·  ${ByteFormatter.format(progress.bytesSeen)} 확인",
                    color = Muted,
                    fontSize = 12.sp,
                )
                if (progress.currentName.isNotBlank()) {
                    Text(
                        progress.currentName,
                        color = Muted,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            TextButton(onClick = onCancel) { Text("중지") }
        }
    }
}

@Composable
private fun ErrorCard(message: String, onDismiss: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF3A2421)),
        shape = RoundedCornerShape(18.dp),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("!", color = Warning, fontSize = 22.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.width(12.dp))
            Text(message, color = Cream, fontSize = 13.sp, modifier = Modifier.weight(1f))
            TextButton(onClick = onDismiss) { Text("닫기") }
        }
    }
}

@Composable
private fun ActionMessageCard(message: String, onDismiss: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1D3420)),
        shape = RoundedCornerShape(18.dp),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("✓", color = Leaf, fontSize = 21.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.width(12.dp))
            Text(message, color = Cream, fontSize = 13.sp, modifier = Modifier.weight(1f))
            TextButton(onClick = onDismiss) { Text("확인") }
        }
    }
}

@Composable
private fun DeletingCard() {
    DashboardCard(containerColor = Color(0xFF352321)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(
                modifier = Modifier.size(32.dp),
                color = MaterialTheme.colorScheme.error,
                trackColor = Outline,
                strokeWidth = 3.dp,
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text("파일 삭제 중", color = Cream, fontWeight = FontWeight.Bold)
                Text("저장소 공급자의 응답을 기다리고 있습니다.", color = Muted, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun SelectionSummaryCard(
    result: ScanResult,
    isScanning: Boolean,
    onRescan: () -> Unit,
    onChooseFolder: () -> Unit,
) {
    DashboardCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(
                        result.root.name,
                        color = Cream,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text("선택 영역", color = Muted, fontSize = 12.sp)
                }
                Text(
                    ByteFormatter.format(result.totalBytes),
                    color = Leaf,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                Metric("파일", "${formatCount(result.fileCount)}개")
                Metric("폴더", "${formatCount(result.directoryCount)}개")
                Metric("분석 시간", formatElapsed(result.elapsedMillis))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onChooseFolder) { Text("폴더 추가") }
                Button(onClick = onRescan, enabled = !isScanning) { Text("다시 분석") }
            }
        }
    }
}

@Composable
private fun Metric(label: String, value: String) {
    Column {
        Text(value, color = Cream, fontWeight = FontWeight.SemiBold)
        Text(label, color = Muted, fontSize = 11.sp)
    }
}

@Composable
private fun TreemapSection(
    result: ScanResult,
    deletionEnabled: Boolean,
    onRequestDelete: (StorageNode) -> Unit,
) {
    var mapRoot by remember(result) { mutableStateOf(result.root) }
    var selectedNode by remember(result) { mutableStateOf<StorageNode?>(null) }
    val entries = remember(mapRoot) { mapRoot.toTreemapEntries() }

    DashboardCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("용량 지도", color = Cream, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text(
                        mapRoot.name,
                        color = Muted,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (mapRoot.uri != result.root.uri) {
                    TextButton(
                        onClick = {
                            mapRoot = result.root
                            selectedNode = null
                        },
                    ) { Text("처음으로") }
                }
            }

            StorageTreemap(
                entries = entries,
                selectedId = selectedNode?.uri,
                onSelect = { entry ->
                    selectedNode = mapRoot.children.firstOrNull { it.uri == entry.id }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF0B0D0C)),
            )

            selectedNode?.let { node ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(SoftInk)
                        .padding(horizontal = 13.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(categoryColor(node.category)),
                    )
                    Spacer(Modifier.width(9.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            node.name,
                            color = Cream,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "${node.category.label}  ·  ${ByteFormatter.format(node.sizeBytes)}",
                            color = Muted,
                            fontSize = 11.sp,
                        )
                    }
                    if (node.isDirectory && node.children.any { it.sizeBytes > 0L }) {
                        TextButton(
                            onClick = {
                                mapRoot = node
                                selectedNode = null
                            },
                        ) { Text("확대") }
                    } else if (!node.isDirectory) {
                        TextButton(
                            onClick = { onRequestDelete(node) },
                            enabled = deletionEnabled,
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.error,
                            ),
                        ) { Text("삭제") }
                    }
                }
            }
            Text("사각형의 넓이가 실제 용량에 비례합니다. 폴더를 선택한 뒤 확대할 수 있습니다.", color = Muted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun CategorySection(categories: List<CategoryUsage>, totalBytes: Long) {
    DashboardCard {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            SectionTitle("파일 유형", "선택 영역의 구성")
            if (categories.isEmpty()) {
                Text("분류할 파일이 없습니다.", color = Muted, fontSize = 13.sp)
            }
            categories.forEach { usage ->
                val fraction = if (totalBytes > 0L) {
                    (usage.sizeBytes.toDouble() / totalBytes.toDouble()).toFloat().coerceIn(0f, 1f)
                } else {
                    0f
                }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(9.dp)
                                .clip(CircleShape)
                                .background(categoryColor(usage.category)),
                        )
                        Spacer(Modifier.width(9.dp))
                        Text(usage.category.label, color = Cream, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        Text(
                            "${ByteFormatter.format(usage.sizeBytes)} · ${(fraction * 100).toInt()}%",
                            color = Muted,
                            fontSize = 12.sp,
                        )
                    }
                    LinearProgressIndicator(
                        progress = { fraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(5.dp)
                            .clip(CircleShape),
                        color = categoryColor(usage.category),
                        trackColor = Outline,
                    )
                }
            }
        }
    }
}

@Composable
private fun FileSearchSection(
    query: String,
    onQueryChange: (String) -> Unit,
    canDeleteFiles: Boolean,
    onChooseFolder: () -> Unit,
) {
    DashboardCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionTitle(
                "파일 찾기·삭제",
                if (query.isBlank()) "공간을 많이 차지하는 파일 20개" else "검색 결과 최대 ${FILE_SEARCH_LIMIT}개",
            )
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("파일 이름 검색") },
                placeholder = { Text("예: video, apk, 보고서") },
                singleLine = true,
            )
            if (!canDeleteFiles) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "삭제하려면 선택한 모든 폴더에 쓰기 권한이 필요합니다. 읽기 전용 폴더를 다시 선택해 주세요.",
                        color = Warning,
                        fontSize = 12.sp,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onChooseFolder) { Text("다시 선택") }
                }
            } else {
                Text(
                    "삭제 버튼을 누른 뒤 파일명과 용량을 다시 확인해야 삭제됩니다.",
                    color = Muted,
                    fontSize = 11.sp,
                )
            }
        }
    }
}

@Composable
private fun FileRow(
    file: StorageNode,
    deletionEnabled: Boolean,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = RaisedInk),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(modifier = Modifier.padding(start = 14.dp, top = 14.dp, end = 10.dp, bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(categoryColor(file.category).copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = categoryGlyph(file.category),
                        color = categoryColor(file.category),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = file.name,
                        color = Cream,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        file.relativePath,
                        color = Muted,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(file.category.label, color = Muted, fontSize = 10.sp)
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    ByteFormatter.format(file.sizeBytes),
                    color = Cream,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onOpen) { Text("열기") }
                TextButton(
                    onClick = onDelete,
                    enabled = deletionEnabled,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) { Text("삭제") }
            }
        }
    }
}

@Composable
private fun DeleteFileDialog(
    file: StorageNode,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("파일을 삭제할까요?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    file.name,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                Text("${file.category.label} · ${ByteFormatter.format(file.sizeBytes)}")
                Text(
                    file.relativePath,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "저장소 공급자에 따라 휴지통을 거치지 않고 즉시 삭제될 수 있으며, 복구하지 못할 수 있습니다.",
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) { Text("삭제") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("취소") }
        },
    )
}

@Composable
private fun SectionTitle(title: String, subtitle: String) {
    Column {
        Text(title, color = Cream, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Text(subtitle, color = Muted, fontSize = 12.sp)
    }
}

@Composable
private fun DashboardCard(
    containerColor: Color = RaisedInk,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = RoundedCornerShape(22.dp),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            content = content,
        )
    }
}

private fun StorageNode.toTreemapEntries(): List<TreemapEntry> {
    val nonEmpty = children.filter { it.sizeBytes > 0L }.sortedByDescending(StorageNode::sizeBytes)
    val visible = nonEmpty.take(MAX_TREEMAP_TILES)
    val entries = visible.map { node ->
        TreemapEntry(
            id = node.uri,
            label = node.name,
            sizeBytes = node.sizeBytes,
            category = node.category,
            isDirectory = node.isDirectory,
        )
    }.toMutableList()

    if (nonEmpty.size > visible.size) {
        entries += TreemapEntry(
            id = "__other__:$uri",
            label = "기타 ${nonEmpty.size - visible.size}개",
            sizeBytes = nonEmpty.drop(visible.size).fold(0L) { sum, node -> safeAdd(sum, node.sizeBytes) },
            category = FileCategory.OTHER,
            isDirectory = false,
        )
    }
    return entries
}

private fun safeAdd(left: Long, right: Long): Long =
    if (Long.MAX_VALUE - left < right) Long.MAX_VALUE else left + right

private fun categoryGlyph(category: FileCategory): String = when (category) {
    FileCategory.IMAGE -> "IMG"
    FileCategory.VIDEO -> "VID"
    FileCategory.AUDIO -> "AUD"
    FileCategory.DOCUMENT -> "DOC"
    FileCategory.ARCHIVE -> "ZIP"
    FileCategory.APP -> "APK"
    FileCategory.OTHER -> "•••"
}

private fun formatCount(value: Int): String = String.format(Locale.KOREA, "%,d", value)

private fun formatElapsed(milliseconds: Long): String = when {
    milliseconds < 1_000L -> "${milliseconds}ms"
    milliseconds < 60_000L -> String.format(Locale.KOREA, "%.1f초", milliseconds / 1_000.0)
    else -> "${milliseconds / 60_000}분 ${(milliseconds % 60_000) / 1_000}초"
}

private const val MAX_TREEMAP_TILES = 35
private const val FILE_SEARCH_LIMIT = 100
