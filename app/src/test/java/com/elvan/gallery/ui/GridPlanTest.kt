package com.elvan.gallery.ui

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.elvan.gallery.data.Media
import org.junit.Assert.assertEquals
import org.junit.Test

class GridPlanTest {
    private fun photo(i: Int) = Media("content://m/$i", "p$i", false, "a", 0, null, 100, 100)

    @Test
    fun squaresAreLaidOutAsTheLazyGridLaysThemOut() {
        val media = (0 until 5).map(::photo)
        val index = media.withIndex().associate { it.value.uri to it.index }
        // 1 px per dp: width 103 with 1 px gaps leaves 100 px, four 25 px columns; the spacer is 10 - 1 = 9 px, then a gap
        val plan = with(Density(1f)) {
            buildPlan(media, index, 4, plain = true, groups = listOf(DayGroup("all", "", media)), rowsOf = { emptyList() },
                width = 103f, topSpacer = 10.dp, prefix = emptyList(), heading = 0f)
        }
        assertEquals(Rect(0f, 10f, 25f, 35f), plan.rect[0])
        assertEquals(Rect(78f, 10f, 103f, 35f), plan.rect[3])
        assertEquals(Rect(0f, 36f, 25f, 61f), plan.rect[4])
        // 40 px down the content is 4 px into the fifth photo's line (item 5: spacer is item 0)
        assertEquals(5 to 4, plan.itemAt(40f))
        assertEquals(0 to 5, plan.itemAt(5f))
    }
}
