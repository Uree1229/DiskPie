package com.diskpie.app.util

import org.junit.Assert.assertEquals
import org.junit.Test

class ByteFormatterTest {
    @Test
    fun `formats binary byte units`() {
        assertEquals("0 B", ByteFormatter.format(0))
        assertEquals("1.00 KB", ByteFormatter.format(1_024))
        assertEquals("1.50 MB", ByteFormatter.format(1_572_864))
        assertEquals("10.0 GB", ByteFormatter.format(10L * 1_024 * 1_024 * 1_024))
    }
}
