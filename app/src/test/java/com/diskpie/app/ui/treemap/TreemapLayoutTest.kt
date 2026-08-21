package com.diskpie.app.ui.treemap

import com.diskpie.app.model.FileCategory
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TreemapLayoutTest {
    private val entries = listOf(
        entry("a", 50),
        entry("b", 30),
        entry("c", 20),
    )

    @Test
    fun `all positive entries are placed inside bounds`() {
        val tiles = TreemapLayout.calculate(entries, width = 300f, height = 200f)

        assertEquals(3, tiles.size)
        tiles.forEach { tile ->
            assertTrue(tile.bounds.left >= 0f)
            assertTrue(tile.bounds.top >= 0f)
            assertTrue(tile.bounds.right <= 300.01f)
            assertTrue(tile.bounds.bottom <= 200.01f)
            assertTrue(tile.bounds.width > 0f)
            assertTrue(tile.bounds.height > 0f)
        }
    }

    @Test
    fun `tiles cover the requested area without overlap`() {
        val tiles = TreemapLayout.calculate(entries, width = 320f, height = 180f)
        val totalArea = tiles.sumOf { (it.bounds.width * it.bounds.height).toDouble() }

        assertTrue(abs(totalArea - 320.0 * 180.0) < 1.0)
        for (leftIndex in tiles.indices) {
            for (rightIndex in leftIndex + 1 until tiles.size) {
                assertTrue(overlapArea(tiles[leftIndex].bounds, tiles[rightIndex].bounds) < 0.01f)
            }
        }
    }

    @Test
    fun `zero sized entries are ignored`() {
        val tiles = TreemapLayout.calculate(entries + entry("empty", 0), 100f, 100f)
        assertEquals(3, tiles.size)
    }

    private fun entry(id: String, size: Long) = TreemapEntry(
        id = id,
        label = id,
        sizeBytes = size,
        category = FileCategory.OTHER,
        isDirectory = false,
    )

    private fun overlapArea(first: RectBounds, second: RectBounds): Float {
        val width = (minOf(first.right, second.right) - maxOf(first.left, second.left)).coerceAtLeast(0f)
        val height = (minOf(first.bottom, second.bottom) - maxOf(first.top, second.top)).coerceAtLeast(0f)
        return width * height
    }
}
