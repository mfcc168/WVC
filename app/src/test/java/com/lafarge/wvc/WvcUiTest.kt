package com.lafarge.wvc

import android.graphics.Bitmap
import androidx.compose.runtime.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
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

    private fun render(state: WvcState = ready, onStop: () -> Unit = {}, onSelect: (String) -> Unit = {}, onDelete: (String) -> Unit = {}) {
        compose.setContent {
            WVCTheme {
                WvcApp(state, onStart = {}, onStop = onStop, onSetupAction = {}, onSelect = onSelect, onSave = { _, _ -> null }, onDelete = onDelete)
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
    @Test fun phoneHomeRenders() {
        render()
        compose.onNodeWithTag("wifi-toggle").assertIsDisplayed().assertIsOff().assertHasClickAction()
        compose.onNodeWithText("Start monitoring").assertDoesNotExist()
        compose.onNodeWithText("Stop monitoring").assertDoesNotExist()
        compose.onNodeWithText("Resume monitoring").assertDoesNotExist()
        screenshot("phone-home")
    }
    @Test @Config(qualifiers = "w1040dp-h900dp-night-xhdpi") fun tabletStaysWhiteInSystemDarkMode() {
        var background = 0
        compose.setContent { WVCTheme {
            background = MaterialTheme.colorScheme.background.toArgb()
            WvcApp(ready, onStart = {}, onStop = {}, onSetupAction = {}, onSelect = {}, onSave = { _, _ -> null }, onDelete = {})
        } }
        compose.runOnIdle { assertEquals(0xFFF5F6F7.toInt(), background) }
        compose.onNodeWithContentDescription("Edit sound settings").assertIsDisplayed()
        compose.onNodeWithText("In Wi-Fi range").assertIsDisplayed()
        screenshot("tablet-home")
    }
    @Test fun stopIsAvailableEvenWhenPermissionsAreMissing() {
        var stopped = false
        render(ready.copy(enabled = true, setup = listOf(SetupItem("location", "Location", "Missing", false, true, "Allow"))), onStop = { stopped = true })
        compose.onNodeWithTag("wifi-toggle").assertIsOn().performClick()
        assertTrue(stopped)
    }
    @Test fun soundPermissionFailureOffersDirectRecoveryAndKeepsStopAvailable() {
        var action = ""
        var stopped = false
        compose.setContent { WVCTheme {
            WvcApp(ready.copy(enabled = true, status = SoundControlAccess.REQUIRED_MESSAGE, recoveryAction = "dnd"),
                onStart = {}, onStop = { stopped = true }, onSetupAction = { action = it },
                onSelect = {}, onSave = { _, _ -> null }, onDelete = {})
        } }
        compose.onNodeWithTag("monitoring-recovery").performScrollTo().performClick()
        assertEquals("dnd", action)
        compose.onNodeWithTag("wifi-toggle").performScrollTo().performClick()
        assertTrue(stopped)
    }
    @Test fun wifiButtonTogglesAndRestartsAfterAndroidPausesMonitoring() {
        var state by mutableStateOf(ready)
        var starts = 0
        var stops = 0
        compose.setContent { WVCTheme {
            WvcApp(state, onStart = { starts++; state = state.copy(enabled = true, recoveryAction = null) },
                onStop = { stops++; state = state.copy(enabled = false, recoveryAction = null) },
                onSetupAction = {}, onSelect = {}, onSave = { _, _ -> null }, onDelete = {})
        } }
        compose.onNodeWithTag("wifi-toggle").assertIsOff().performClick()
        compose.onNodeWithTag("wifi-toggle").assertIsOn().performClick()
        compose.onNodeWithTag("wifi-toggle").assertIsOff()
        assertEquals(1, starts)
        assertEquals(1, stops)
        compose.runOnIdle { state = state.copy(enabled = true, recoveryAction = "resume") }
        compose.onNodeWithTag("wifi-toggle").assertIsOff().performClick()
        compose.onNodeWithTag("wifi-toggle").assertIsOn()
        assertEquals(2, starts)
        assertEquals(1, stops)
    }
    @Test fun wifiButtonGuidesProfileCreationAndMissingPermissions() {
        var state by mutableStateOf(WvcState())
        var starts = 0
        compose.setContent { WVCTheme {
            WvcApp(state, onStart = { starts++ }, onStop = {}, onSetupAction = {},
                onSelect = {}, onSave = { _, _ -> null }, onDelete = {})
        } }
        compose.onNodeWithTag("wifi-toggle").performClick()
        compose.onNodeWithTag("profile-editor").assertExists()
        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle { state = ready.copy(setup = listOf(SetupItem("location", "Precise location", "Missing", false, true, "Allow access"))) }
        compose.onNodeWithTag("wifi-toggle").performClick()
        compose.onNodeWithText("App setup").assertIsDisplayed()
        assertEquals(0, starts)
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
        compose.onNodeWithTag("wifi-toggle").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("App setup").performClick()
        compose.onNodeWithText("Precise location").performScrollTo().assertIsDisplayed()
        screenshot("compact-large-text-setup")
    }
    @Test fun emptyStateAndProfileActionsAreAccessible() {
        render(WvcState())
        compose.onNodeWithText("Profile", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Create profile", useUnmergedTree = true).performClick()
        compose.onNodeWithTag("profile-name").assertExists()
    }

    @Test fun homePickerSelectsProfileWithoutOpeningEditor() {
        val office = home.copy(name = "Office", ssid = "Office_WiFi")
        var selected = ""
        render(ready.copy(profiles = listOf(home, office)), onSelect = { selected = it })
        compose.onNodeWithTag("choose-profile").performScrollTo().performClick()
        compose.onNodeWithText("Office").performClick()
        assertEquals("Office", selected)
        compose.onNodeWithTag("profile-editor").assertDoesNotExist()
        compose.onNodeWithText("Choose a profile").assertDoesNotExist()
    }
    @Test fun profilePageHasTwoIconTabsAndEditsExistingProfile() {
        render()
        compose.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)).assertCountEquals(2)
        compose.onNodeWithTag("tab-Home").assertIsSelected()
        compose.onNodeWithTag("tab-Profile").assertIsNotSelected()
        compose.onNodeWithText("Profile", useUnmergedTree = true).performClick()
        compose.onNodeWithTag("tab-Profile").assertIsSelected()
        compose.onNodeWithTag("tab-Home").assertIsNotSelected()
        compose.onNodeWithContentDescription("Edit Home").performScrollTo().performClick()
        compose.onNodeWithTag("profile-name").assertTextContains("Home")
        compose.onNodeWithTag("profile-ssid").assertTextContains("Home_WiFi")
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithTag("profile-editor").assertDoesNotExist()
    }
    @Test fun deletingActiveProfileRequiresConfirmation() {
        var deleted = ""
        render(onDelete = { deleted = it })
        compose.onNodeWithText("Profile", useUnmergedTree = true).performClick()
        compose.onNodeWithContentDescription("Delete Home").performScrollTo().performClick()
        assertEquals("", deleted)
        compose.onNodeWithText("Delete profile").performClick()
        assertEquals("Home", deleted)
    }
    @Test fun profilePageScreenshot() {
        render(ready.copy(profiles = listOf(home, home.copy(name = "Office", ssid = "Studio_WiFi"))))
        compose.onNodeWithText("Profile", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Create profile").assertIsDisplayed()
        compose.onNodeWithContentDescription("App setup").assertIsDisplayed()
        screenshot("phone-profiles")
    }
    @Test fun activeMonitoringAndProfilePickerRemainInteractiveDuringWaves() {
        render(ready.copy(enabled = true, status = "Home_WiFi is nearby. Inside volumes applied."))
        compose.onNodeWithContentDescription("Wi-Fi monitoring").assertIsDisplayed().assertIsOn()
        compose.onNodeWithTag("choose-profile").performScrollTo().performClick()
        compose.onNodeWithText("Choose a profile").assertIsDisplayed()
        compose.onNodeWithContentDescription("Close Choose a profile").performClick()
        compose.onNodeWithTag("wifi-toggle").performScrollTo().assertIsDisplayed()
        screenshot("phone-monitoring")
    }

    @Test fun wifiWavesMoveWhileEnabledAndSettleWhenPaused() {
        compose.mainClock.autoAdvance = false
        var enabled by mutableStateOf(true)
        compose.setContent { WVCTheme { WifiScanner(enabled, visible = true) } }
        fun frame(): Bitmap {
            var bitmap: Bitmap? = null
            compose.runOnIdle {
                val view = WindowInspector.getGlobalWindowViews().last()
                bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                view.draw(Canvas(bitmap!!))
            }
            return bitmap!!
        }
        compose.mainClock.advanceTimeBy(100)
        val first = frame()
        compose.mainClock.advanceTimeBy(900)
        val moving = frame()
        assertFalse("Enabled waves should advance", first.sameAs(moving))
        compose.runOnIdle { enabled = false }
        compose.mainClock.advanceTimeBy(600)
        val paused = frame()
        compose.mainClock.advanceTimeBy(1000)
        val still = frame()
        assertTrue("Paused waves should stop drawing new animation frames", paused.sameAs(still))
        listOf(first, moving, paused, still).forEach { it.recycle() }
    }

    @Test fun profileChangePopupMatchesThemeAndDismisses() {
        var notice by mutableStateOf<ProfileNotice?>(ProfileNotice.applied(AppliedSoundProfile("Office", "Office_WiFi", true, 25, 15)))
        compose.setContent { WVCTheme {
            WvcApp(ready, onStart = {}, onStop = {}, onSetupAction = {}, onSelect = {}, onSave = { _, _ -> null }, onDelete = {},
                profileNotice = notice, onNoticeDismissed = { notice = null })
        } }
        compose.onNodeWithTag("profile-change-popup").assertIsDisplayed()
        compose.onNodeWithText("SOUND UPDATED").assertIsDisplayed()
        compose.onNodeWithText("Office").assertIsDisplayed()
        screenshot("profile-change-popup")
        compose.onNodeWithContentDescription("Dismiss profile notification").performClick()
        compose.onNodeWithTag("profile-change-popup").assertDoesNotExist()
    }
    @Test fun newPopupGetsItsOwnTimeoutAndSelectionDoesNotClaimAppliedVolumes() {
        compose.mainClock.autoAdvance = false
        var notice by mutableStateOf<ProfileNotice?>(ProfileNotice("Home", "Waiting for a fresh scan.", id = 1))
        compose.setContent { WVCTheme { ProfileChangePopup(notice) { notice = null } } }
        compose.mainClock.advanceTimeBy(400)
        compose.onNodeWithText("PROFILE SELECTED").assertIsDisplayed()
        compose.onNodeWithText("SOUND UPDATED").assertDoesNotExist()
        compose.mainClock.advanceTimeBy(4000)
        compose.runOnIdle { notice = ProfileNotice.applied(AppliedSoundProfile("Office", "WiFi", false, 80, 65)).copy(id = 2) }
        compose.mainClock.advanceTimeBy(2500)
        compose.onNodeWithText("Office").assertIsDisplayed()
        compose.mainClock.advanceTimeBy(4500)
        compose.onNodeWithTag("profile-change-popup").assertDoesNotExist()
    }
}
