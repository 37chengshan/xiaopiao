package com.xiaopiao.app.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SegmentedTest {

    @get:Rule val rule = createComposeRule()

    private fun render(initial: Int = 0, onSelect: (Int) -> Unit = {}) {
        val selected = mutableIntStateOf(initial)
        rule.setContent {
            MaterialTheme {
                Segmented(
                    options = listOf("跟随系统", "浅色", "深色"),
                    selected = selected.intValue,
                    onSelect = {
                        selected.intValue = it
                        onSelect(it)
                    },
                )
            }
        }
    }

    @Test
    fun tapSelectsAndUpdatesSemantics() {
        var picked = -1
        render(onSelect = { picked = it })
        rule.onNodeWithContentDescription("深色").performClick()
        rule.waitForIdle()
        assertEquals(2, picked)
        rule.onNodeWithContentDescription("深色").assertIsSelected()
    }

    @Test
    fun draggingTheThumbSnapsToTheNearestOption() {
        var picked = -1
        render(onSelect = { picked = it })
        rule.onNodeWithTag("segmented-thumb").performTouchInput {
            down(center)
            moveBy(Offset(width * 2f, 0f), delayMillis = 120)
            up()
        }
        rule.mainClock.advanceTimeBy(600)
        rule.waitForIdle()
        assertEquals(2, picked)
        rule.onNodeWithContentDescription("深色").assertIsSelected()
    }

    @Test
    fun aSmallDragSnapsBackWithoutCommitting() {
        var picked = -1
        render(initial = 1, onSelect = { picked = it })
        rule.onNodeWithTag("segmented-thumb").performTouchInput {
            down(center)
            moveBy(Offset(width * 0.3f, 0f), delayMillis = 80)
            up()
        }
        rule.mainClock.advanceTimeBy(600)
        rule.waitForIdle()
        assertEquals(-1, picked)
        rule.onNodeWithContentDescription("浅色").assertIsSelected()
    }
}
