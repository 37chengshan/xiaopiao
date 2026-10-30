package com.xiaopiao.app.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SnapPickerTest {

    @get:Rule val rule = createComposeRule()

    @Test
    fun tappingAFarItemCommitsOnlyTheTarget() {
        val calls = mutableListOf<Int>()
        rule.setContent {
            MaterialTheme {
                SnapPicker(
                    items = listOf("1", "2", "3"),
                    selectedIndex = 0,
                    onSelect = { calls += it },
                    itemWidth = 72.dp,
                    label = { it },
                ) { item, _ -> Text(item) }
            }
        }
        rule.onNodeWithContentDescription("3").performClick()
        rule.waitForIdle()
        assertEquals(listOf(2), calls)
    }
}
