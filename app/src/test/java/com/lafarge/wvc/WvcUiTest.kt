package com.lafarge.wvc

import android.graphics.Bitmap
import androidx.compose.runtime.CompositionLocalProvider
import android.graphics.Canvas
import android.view.inspector.WindowInspector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.unit.Density
import com.lafarge.wvc.ui.*
import com.lafarge.wvc.ui.theme.WVCTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w390dp-h844dp-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WvcUiTest {
    @get:Rule val compose = createComposeRule()
    private val home = VolumeProfile("Home", "Home_WiFi", mapOf(VolumeProfile.RINGTONE_INDOOR to 25, VolumeProfile.NOTIFICATION_INDOOR to 15, VolumeProfile.RINGTONE_OUTDOOR to 80, VolumeProfile.NOTIFICATION_OUTDOOR to 65))
    private val ready = WvcState(listOf(home), "Home", setup = listOf(SetupItem("location", "Precise location", "Access is ready", true, true, "Allow access")))

    private fun render(state: WvcState = ready, dark: Boolean = false, onStop: () -> Unit = {}) {
        compose.setContent {
            WVCTheme(darkTheme = dark) {
                WvcApp(state, onStart = {}, onStop = onStop, onSetupAction = {}, onSelect = {}, onSave = { _, _ -> null }, onDelete = {})
            }
        }
    }
    private fun screenshot(name: String) {
        compose.waitForIdle()
        val file = File("build/reports/screenshots/$name.png").apply { parentFile?.mkdirs() }
        // Draw the real Compose window with Robolectric native graphics. Compose 1.6's
        // PixelCopy helper waits for a hardware frame that this JVM runner does not emit.
        compose.runOnIdle {
            val view = WindowInspector.getGlobalWindowViews().last()
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }
    @Test fun phoneOverviewRenders() {
        render()
        compose.onNodeWithText("Start monitoring").assertIsDisplayed()
        screenshot("phone-overview")
    }
    @Test @Config(qualifiers = "w1040dp-h900dp-xhdpi") fun tabletUsesRailAndTwoColumnsInDarkMode() {
        render(dark = true)
        compose.onNodeWithText("Edit sound settings").assertIsDisplayed()
        compose.onNodeWithText("Inside the area").assertIsDisplayed()
        screenshot("tablet-dark")
    }
    @Test fun stopIsAvailableEvenWhenPermissionsAreMissing() {
        var stopped = false
        render(ready.copy(enabled = true, setup = listOf(SetupItem("location", "Location", "Missing", false, true, "Allow"))), onStop = { stopped = true })
        compose.onNodeWithText("Stop monitoring").performClick()
        assertTrue(stopped)
    }
    @Test fun editorValidatesWithoutClosingAndDoesNotSaveWhileTyping() {
        var saves = 0
        compose.setContent { WVCTheme { ProfileEditor(null, listOf(home), {}, { saves++; null }) } }
        compose.onNodeWithTag("profile-name").performTextInput("home")
        compose.onNodeWithTag("save-profile").performClick()
        compose.onNodeWithText("A profile with this name already exists.").assertExists()
        assertEquals(0, saves)
        compose.onNodeWithTag("profile-name").performTextReplacement("Office")
        compose.onNodeWithTag("profile-ssid").performTextInput("Office_WiFi")
        assertEquals(0, saves)
        compose.onNodeWithTag("save-profile").performClick()
        assertEquals(1, saves)
    }
    @Test fun unsavedDraftSurvivesRecreationAndCancelNeedsConfirmation() {
        var dismissed = false
        val restoration = StateRestorationTester(compose)
        restoration.setContent { WVCTheme { ProfileEditor(null, emptyList(), { dismissed = true }, { null }) } }
        compose.onNodeWithTag("profile-name").performTextInput("Office")
        compose.onNodeWithTag("profile-ssid").performTextInput("Office_WiFi")
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("profile-name").assertTextContains("Office")
        compose.onNodeWithTag("profile-ssid").assertTextContains("Office_WiFi")
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("Discard unsaved edits?").assertIsDisplayed()
        assertFalse(dismissed)
        compose.onNodeWithText("Discard", useUnmergedTree = true).performClick()
        assertTrue(dismissed)
    }
    @Test fun editorPhoneScreenshot() {
        compose.setContent { WVCTheme { ProfileEditor(home, listOf(home), {}, { null }) } }
        compose.onNodeWithTag("save-profile").assertIsDisplayed()
        screenshot("phone-editor")
    }
    @Test @Config(qualifiers = "w320dp-h700dp-xhdpi") fun compactScreenWithLargeTextKeepsNavigationAndActionsReachable() {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.6f)) {
                WVCTheme { WvcApp(ready, onStart = {}, onStop = {}, onSetupAction = {}, onSelect = {}, onSave = { _, _ -> null }, onDelete = {}) }
            }
        }
        compose.onNodeWithText("Start monitoring").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Setup", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Precise location").performScrollTo().assertIsDisplayed()
        screenshot("compact-large-text-setup")
    }
    @Test fun emptyStateAndProfileActionsAreAccessible() {
        render(WvcState())
        compose.onNodeWithText("Profiles", useUnmergedTree = true).performClick()
        compose.onNodeWithText("New profile", useUnmergedTree = true).performClick()
        compose.onNodeWithTag("profile-name").assertExists()
    }
}
