package com.diskpie.app.ui.treemap

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.diskpie.app.model.FileCategory
import com.diskpie.app.ui.theme.Cream
import com.diskpie.app.util.ByteFormatter

@Composable
fun StorageTreemap(
    entries: List<TreemapEntry>,
    selectedId: String?,
    onSelect: (TreemapEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.height(278.dp)) {
        val width = maxWidth.value
        val height = maxHeight.value
        val tiles = remember(entries, width, height) {
            TreemapLayout.calculate(entries, width, height)
        }

        if (tiles.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "표시할 크기 정보가 없습니다",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        tiles.forEach { tile ->
            val bounds = tile.bounds
            val selected = selectedId == tile.entry.id
            val tileWidth = (bounds.width - 2f).coerceAtLeast(1f).dp
            val tileHeight = (bounds.height - 2f).coerceAtLeast(1f).dp
            Box(
                modifier = Modifier
                    .offset(x = bounds.left.dp, y = bounds.top.dp)
                    .size(width = tileWidth, height = tileHeight)
                    .clip(RoundedCornerShape(5.dp))
                    .background(treemapEntryColor(tile.entry))
                    .then(
                        if (selected) {
                            Modifier.border(2.dp, Cream, RoundedCornerShape(5.dp))
                        } else {
                            Modifier
                        },
                    )
                    .semantics {
                        contentDescription = buildString {
                            append(tile.entry.label)
                            append(", ")
                            append(ByteFormatter.format(tile.entry.sizeBytes))
                        }
                    }
                    .clickable { onSelect(tile.entry) }
                    .padding(7.dp),
            ) {
                if (bounds.width >= 68f && bounds.height >= 38f) {
                    Text(
                        text = tile.entry.label,
                        color = Color(0xFF0E1510),
                        fontSize = if (bounds.width >= 120f) 12.sp else 10.sp,
                        lineHeight = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = if (bounds.height >= 58f) 2 else 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

fun categoryColor(category: FileCategory): Color = when (category) {
    FileCategory.IMAGE -> Color(0xFF69D2E7)
    FileCategory.VIDEO -> Color(0xFFA68BE8)
    FileCategory.AUDIO -> Color(0xFFFFB86B)
    FileCategory.DOCUMENT -> Color(0xFF9FE870)
    FileCategory.ARCHIVE -> Color(0xFFFF83B5)
    FileCategory.APP -> Color(0xFFFFDB65)
    FileCategory.OTHER -> Color(0xFF89958B)
}

fun appPaletteColor(index: Int): Color = APP_PALETTE[Math.floorMod(index, APP_PALETTE.size)]

private fun treemapEntryColor(entry: TreemapEntry): Color =
    entry.paletteIndex?.let(::appPaletteColor) ?: categoryColor(entry.category)

private val APP_PALETTE = listOf(
    Color(0xFFFFDB65),
    Color(0xFF9FE870),
    Color(0xFF69D2E7),
    Color(0xFFA68BE8),
    Color(0xFFFF9C73),
    Color(0xFFFF83B5),
    Color(0xFF78C6A3),
    Color(0xFFC7B8FF),
)
