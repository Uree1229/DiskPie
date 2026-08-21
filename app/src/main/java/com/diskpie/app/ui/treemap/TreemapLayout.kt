package com.diskpie.app.ui.treemap

import com.diskpie.app.model.FileCategory
import kotlin.math.max
import kotlin.math.min

data class TreemapEntry(
    val id: String,
    val label: String,
    val sizeBytes: Long,
    val category: FileCategory,
    val isDirectory: Boolean,
    val paletteIndex: Int? = null,
)

data class RectBounds(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val width: Float get() = (right - left).coerceAtLeast(0f)
    val height: Float get() = (bottom - top).coerceAtLeast(0f)
}

data class TreemapTile(
    val entry: TreemapEntry,
    val bounds: RectBounds,
)

/** A deterministic implementation of the squarified treemap algorithm. */
object TreemapLayout {
    fun calculate(
        entries: List<TreemapEntry>,
        width: Float,
        height: Float,
    ): List<TreemapTile> {
        if (width <= 0f || height <= 0f) return emptyList()
        val positiveEntries = entries.filter { it.sizeBytes > 0L }.sortedByDescending { it.sizeBytes }
        val totalWeight = positiveEntries.sumOf { it.sizeBytes.toDouble() }
        if (totalWeight <= 0.0) return emptyList()

        val scale = width.toDouble() * height.toDouble() / totalWeight
        val pending = positiveEntries.map { SizedEntry(it, it.sizeBytes.toDouble() * scale) }
        val result = mutableListOf<TreemapTile>()
        var remaining = RectBounds(0f, 0f, width, height)
        var cursor = 0

        while (cursor < pending.size && remaining.width > 0.01f && remaining.height > 0.01f) {
            val side = min(remaining.width, remaining.height).toDouble()
            val row = mutableListOf<SizedEntry>()
            row += pending[cursor++]

            while (cursor < pending.size) {
                val candidate = pending[cursor]
                if (worstAspect(row + candidate, side) <= worstAspect(row, side)) {
                    row += candidate
                    cursor++
                } else {
                    break
                }
            }

            remaining = placeRow(row, remaining, result)
        }

        return result
    }

    private fun placeRow(
        row: List<SizedEntry>,
        available: RectBounds,
        output: MutableList<TreemapTile>,
    ): RectBounds {
        val rowArea = row.sumOf(SizedEntry::area).toFloat()
        if (rowArea <= 0f) return available

        if (available.width >= available.height) {
            val stripWidth = (rowArea / available.height).coerceAtMost(available.width)
            var top = available.top
            row.forEachIndexed { index, item ->
                val itemHeight = if (index == row.lastIndex) {
                    available.bottom - top
                } else {
                    (item.area / stripWidth).toFloat()
                }
                output += TreemapTile(
                    item.entry,
                    RectBounds(available.left, top, available.left + stripWidth, top + itemHeight),
                )
                top += itemHeight
            }
            return RectBounds(
                left = available.left + stripWidth,
                top = available.top,
                right = available.right,
                bottom = available.bottom,
            )
        }

        val stripHeight = (rowArea / available.width).coerceAtMost(available.height)
        var left = available.left
        row.forEachIndexed { index, item ->
            val itemWidth = if (index == row.lastIndex) {
                available.right - left
            } else {
                (item.area / stripHeight).toFloat()
            }
            output += TreemapTile(
                item.entry,
                RectBounds(left, available.top, left + itemWidth, available.top + stripHeight),
            )
            left += itemWidth
        }
        return RectBounds(
            left = available.left,
            top = available.top + stripHeight,
            right = available.right,
            bottom = available.bottom,
        )
    }

    private fun worstAspect(row: List<SizedEntry>, side: Double): Double {
        if (row.isEmpty() || side <= 0.0) return Double.POSITIVE_INFINITY
        val sum = row.sumOf(SizedEntry::area)
        val largest = row.maxOf(SizedEntry::area)
        val smallest = row.minOf(SizedEntry::area)
        if (smallest <= 0.0 || sum <= 0.0) return Double.POSITIVE_INFINITY
        val sideSquared = side * side
        val sumSquared = sum * sum
        return max(
            sideSquared * largest / sumSquared,
            sumSquared / (sideSquared * smallest),
        )
    }

    private data class SizedEntry(
        val entry: TreemapEntry,
        val area: Double,
    )
}
