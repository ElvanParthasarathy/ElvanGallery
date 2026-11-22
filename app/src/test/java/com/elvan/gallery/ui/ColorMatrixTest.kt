package com.elvan.gallery.ui

import org.junit.Assert.assertArrayEquals
import org.junit.Test

class ColorMatrixTest {
    private val identity = photoFilters.first().matrix

    @Test fun originalWithNoAdjustmentsChangesNothing() {
        assertArrayEquals(identity, adjustMatrix(identity, 0f, 0f, 0f, 0f), 1e-4f)
    }

    @Test fun concatAppliesTheFirstThenTheSecond() {
        val brighten = floatArrayOf(1f, 0f, 0f, 0f, 10f, 0f, 1f, 0f, 0f, 10f, 0f, 0f, 1f, 0f, 10f, 0f, 0f, 0f, 1f, 0f)
        val double = floatArrayOf(2f, 0f, 0f, 0f, 0f, 0f, 2f, 0f, 0f, 0f, 0f, 0f, 2f, 0f, 0f, 0f, 0f, 0f, 1f, 0f)
        // Brighten by 10, then double: (x + 10) * 2 = 2x + 20
        val m = concat(double, brighten)
        assertArrayEquals(floatArrayOf(2f, 0f, 0f, 0f, 20f), m.copyOfRange(0, 5), 1e-4f)
        assertArrayEquals(identity, concat(identity, identity), 1e-4f)
    }
}
