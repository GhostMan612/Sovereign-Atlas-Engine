// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sovereignatlas.atlas.geo.AtlasCoordinate
import com.sovereignatlas.atlas.measure.MeasureState
import com.sovereignatlas.atlas.measure.MeasureUnit
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
final class MeasurePanelTest {
    @get:Rule
    val compose = createComposeRule()

    private fun populated(): MeasureState {
        val state = MeasureState()
        state.begin(
            fixA = AtlasCoordinate(latitude = 44.9778, longitude = -93.2650),
            center = AtlasCoordinate(latitude = 0.0, longitude = 0.0),
        )
        state.setB(AtlasCoordinate(latitude = 44.9878, longitude = -93.2550))
        return state
    }

    @Test
    fun panelShowsMeasureReadouts() {
        val state = populated()
        compose.setContent {
            MeasurePanel(
                snapshot = state.snapshot(),
                units = MeasureUnit.values().toList(),
                onUnitSelected = { state.setUnit(it) },
                onClose = {},
                onClear = { state.clear() },
            )
        }
        compose.onNodeWithTag("measure-panel").assertIsDisplayed()
        compose.onNodeWithText("Measure").assertIsDisplayed()
        compose.onNodeWithText("A (GPS): 44.9778, -93.2650").assertIsDisplayed()
        compose.onNodeWithText("B: 44.9878, -93.2550").assertIsDisplayed()
        compose.onNodeWithText("Clear measurement").assertIsDisplayed()
        compose.onNodeWithText("Close").assertIsDisplayed()
    }

    @Test
    fun unitButtonCyclesAndClearResets() {
        val state = populated()
        var cleared = 0
        compose.setContent {
            MeasurePanel(
                snapshot = state.snapshot(),
                units = MeasureUnit.values().toList(),
                onUnitSelected = { state.setUnit(it) },
                onClose = {},
                onClear = {
                    cleared++
                    state.clear()
                },
            )
        }
        compose.onNodeWithTag("measure-unit").performClick()
        assertEquals(MeasureUnit.kilometers, state.unit())
        compose.onNodeWithTag("measure-clear").performClick()
        assertEquals(1, cleared)
        assertEquals(false, state.isActive())
    }

    @Test
    fun waitingStateMatchesFlutter() {
        val state = MeasureState()
        compose.setContent {
            MeasurePanel(
                snapshot = state.snapshot(),
                units = MeasureUnit.values().toList(),
                onUnitSelected = { state.setUnit(it) },
                onClose = {},
                onClear = { state.clear() },
            )
        }
        compose.onNodeWithText("A: waiting").assertIsDisplayed()
        compose.onNodeWithText("B: tap the map to set point B").assertIsDisplayed()
        compose.onNodeWithText("Distance: —").assertIsDisplayed()
        compose.onNodeWithText("Bearing: undefined").assertIsDisplayed()
    }
}
