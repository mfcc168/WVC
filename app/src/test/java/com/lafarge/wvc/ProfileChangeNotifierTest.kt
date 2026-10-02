package com.lafarge.wvc

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ProfileChangeNotifierTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val manager = context.getSystemService(NotificationManager::class.java)
    private val home = AppliedSoundProfile("Home", "Private_WiFi", true, 25, 15)
    private fun posted() = shadowOf(manager).getNotification(ProfileChangeNotifier.NOTIFICATION_ID)
    @Before fun prepare() {
        MonitoringSettings.prefs(context).edit().clear().commit()
        manager.cancelAll()
        shadowOf(context as android.app.Application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
    }

    @Test fun backgroundChangeUsesQuietHighImportanceChannelAndPrivateDetails() {
        ProfileChangeNotifier.applied(context, home, false)
        val channel = manager.getNotificationChannel(ProfileChangeNotifier.CHANNEL)
        assertEquals(NotificationManager.IMPORTANCE_HIGH, channel.importance)
        assertNull(channel.sound)
        assertFalse(channel.shouldVibrate())
        assertFalse(channel.canBypassDnd())
        val notification = posted()!!
        assertEquals("Home · In Wi-Fi range", notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertEquals(home.summary, notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
        assertEquals(Notification.VISIBILITY_PRIVATE, notification.visibility)
        assertEquals("Sound profile updated", notification.publicVersion.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertTrue(notification.flags and Notification.FLAG_AUTO_CANCEL != 0)
        assertEquals(MainActivity::class.java.name, shadowOf(notification.contentIntent).savedIntent.component?.className)
        assertEquals(60_000L, notification.timeoutAfter)
    }
    @Test fun repeatedScansAndServiceRestartsDoNotRepeatAnUnchangedProfile() {
        ProfileChangeNotifier.applied(context, home, false)
        manager.cancelAll()
        ProfileChangeNotifier.applied(context, home.copy(), false)
        assertNull(posted())
    }
    @Test fun changedAreaNameOrVolumesRefreshesTheSingleNotification() {
        ProfileChangeNotifier.applied(context, home, false)
        ProfileChangeNotifier.applied(context, home.copy(indoor = false, ringtone = 80), true)
        assertEquals("Home · Out of Wi-Fi range", posted()!!.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        ProfileChangeNotifier.applied(context, home.copy(name = "Office"), false)
        assertEquals("Office · In Wi-Fi range", posted()!!.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertEquals(1, shadowOf(manager).allNotifications.size)
    }
    @Test fun reapplyingAfterManualVolumeChangeCanNotifyAgain() {
        ProfileChangeNotifier.applied(context, home, false)
        manager.cancelAll()
        ProfileChangeNotifier.applied(context, home, true)
        assertNotNull(posted())
    }
    @Test fun foregroundShowsAnEventWithoutASecondSystemPopupOrStaleReplay() = runBlocking {
        val next = async(start = CoroutineStart.UNDISPATCHED) { ProfileChangeNotifier.events.first() }
        ProfileChangeNotifier.applied(context, home, false)
        assertEquals(home, next.await())
        assertNull(posted())
        assertTrue(ProfileChangeNotifier.events.replayCache.isEmpty())
    }
    @Test fun deniedPermissionDoesNotPost() {
        shadowOf(context as android.app.Application).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        ProfileChangeNotifier.applied(context, home, false)
        assertNull(posted())
    }
    @Test fun disabledChannelIsNotReenabledByApplication() {
        manager.createNotificationChannel(android.app.NotificationChannel(ProfileChangeNotifier.CHANNEL, "Disabled", NotificationManager.IMPORTANCE_NONE))
        ProfileChangeNotifier.applied(context, home.copy(indoor = false), false)
        assertNull(posted())
    }
}
