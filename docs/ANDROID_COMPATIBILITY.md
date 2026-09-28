# Wi-Fi volume monitoring: review and compatibility

## Existing structure and defects

The app is a single Kotlin/Compose activity, JSON profiles in SharedPreferences,
WiFiScanService, and a boot receiver. That structure is sufficient for this small
app; a database, server, or large architecture rewrite is not needed.

The previous implementation requested scans every 10 seconds (twice at startup),
ignored scan success/freshness, and reapplied sound levels on every broadcast.
Android throttles scans, so cached results could make the app choose the wrong
profile and repeated writes could undo a user's manual volume adjustment.
MainActivity.onDestroy also stopped the service, including on activity recreation.
The Start indicator was UI-only; Stop was not persisted and reboot could re-enable
it. Background location was declared but never requested. Startup exceptions were
not handled. Alarm-based restarts do not provide reliable background execution.
Notification policy access needed for some silent-volume transitions was missing.

## Design implemented

- Retain **nearby SSID detection**, including networks the phone is not connected
  to. Use a user-enabled location foreground service and a persistent notification.
- Request scans no more often than every two minutes and consume system scan
  broadcasts. Android can throttle further; this is not a timing guarantee.
- Require successful scan broadcasts and observations at most 30 seconds old.
  Duplicate/out-of-order snapshots do not advance the presence state machine.
  One fresh sighting enters the area; two consecutive fresh misses leave it.
  Empty, failed, stale, disabled-Wi-Fi and unavailable-location results hold the
  existing volume. In a place with no detectable access points, absence cannot
  be timestamp-verified and the old volume is intentionally retained.
- Apply volumes on a confirmed state/configuration change, not every scan.
  Respect active global DND; report denied or silently ignored audio changes.
  Ringtone/notification streams may be linked by the device manufacturer.
- Persist user enable/disable intent independently of service lifetime. Do not
  stop monitoring when the activity closes. Use START_STICKY, with no exact alarm
  or attempt to bypass force-stop or Android's background restrictions.
- Keep named profiles and legacy settings, including saving legacy sliders.
- Show service status and explicit permission/settings controls. Request coarse
  and precise location together, notifications only on API 33+, and background
  location separately. Location services must also be on.
- Android 17 requires a user-started foreground service with while-in-use
  capabilities for background volume APIs. After reboot/update, show a Resume
  notification instead of running a boot-started service that silently cannot
  change volume. Open WVC and tap Resume. If notifications are denied, open WVC
  manually. A foreground Resume recreates the service to obtain those capabilities.

## Alternatives

| Approach | Suitable use | Tradeoff |
|---|---|---|
| Nearby SSID scans (retained) | Detect an area without connecting to its network | Throttled; delayed in sleep; SSIDs are not unique; empty results cannot prove departure |
| ConnectivityManager network callbacks | Change sound only when connected to the selected Wi-Fi | Less scanning and faster connection events, but being nearby is no longer enough |
| Geofencing | Larger home/work areas | Geographic coordinates, location permission, usually Play services, coarse boundaries and delayed events |
| Periodic WorkManager | Occasional reconciliation | Minimum periodic interval and scheduling delays; cannot deliver continuous proximity or bypass Android 17 audio rules |

For a future optional mode, connection-based detection is the best lower-power
choice if connecting to the Wi-Fi is acceptable. It should not silently replace
the requested nearby-area behavior.

## Compatibility and setup

Build: compile/target API 37 (Android 17), minimum API 26 (Android 8), AGP 9.3.0,
Gradle 9.5.0, built-in Kotlin with Compose compiler 2.2.10. Use JDK 21 for CI
(AGP's documented minimum is 17). The old Crashlytics **build tools** runtime
library was removed; it was not a Crashlytics SDK integration.

| Android | Required behavior |
|---|---|
| 8–9 / API 26–28 | Fine location; location services; visible foreground notification; boot resumes only if enabled |
| 10 / API 29 | Separately grant background location for reboot |
| 11–12L / API 30–32 | Grant "Allow all the time" in app settings; Android 12 needs precise rather than approximate location |
| 13 / API 33 | Notification permission controls notification visibility; deny does not itself prevent foreground monitoring |
| 14–16 / API 34–36 | Typed location foreground service; background startup permission checks; DND remains user-controlled |
| 17 / API 37 | Same permissions, plus explicit Resume after boot/update for background audio capabilities |

After upgrading from 1.0, tap Start once to opt into the new persisted monitoring setting.
Grant notification policy access if using silent volume settings. WVC does not
promise to disable a user's DND mode on exit. Manufacturer battery restrictions,
force-stop, revoked permissions, and Doze can stop or delay work. After force-stop,
open WVC and tap Resume. No application can guarantee uninterrupted 24/7 execution.

## Validation

`bash gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`

Local validation on 2026-09-28 completed successfully with JDK 21: debug APK built,
18 JVM tests passed, including profile storage and Compose interactions, and lint
reported 0 errors. Non-blocking dependency, resource and style warnings remain.
Physical-device checks below have not been performed.

The UI redesign and native preview renders are described in [UI_REDESIGN.md](UI_REDESIGN.md).

The workflow uploads reports and the debug APK. PresenceTracker unit tests cover
initial uncertainty, consecutive misses, duplicate/out-of-order observations and
re-entry. Passing a build does not certify real radio behavior.

Before release, test on physical devices at API 26/29/31/33/34/35/36/37:

1. Fresh install: deny location, grant approximate, then precise; never crash or
   claim volume was changed without access. Test Location services off/on.
2. Start, background, rotate, close the activity, screen off, and leave overnight.
   Confirm activity destruction does not stop monitoring.
3. Enter/leave SSID range while unconnected. Confirm two fresh misses are required;
   failed/stale scans, duplicate broadcasts and Wi-Fi-off do not apply outdoors.
4. Change profile/volume settings; confirm next fresh observation uses new values.
5. Stop then reboot: no restart. Enable then reboot: API 26–36 resumes if permitted;
   API 37 shows Resume. Test denied background location and notifications.
6. Test zero volume, linked ringtone/notification streams, DND on/off, revoked
   notification policy access, and manually adjusted volume between transitions.
7. Force-stop and reopen; test an OS-restarted service. On API 37 use
   `adb shell cmd audio set-enable-hardening throw`, then verify Resume recovery.
8. Verify all controls remain outside system bars with Android 15–17 edge-to-edge.

## Official references

- https://developer.android.com/develop/connectivity/wifi/wifi-scan
- https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start
- https://developer.android.com/develop/background-work/services/fgs/service-types
- https://developer.android.com/about/versions/17/changes/bg-audio
- https://developer.android.com/about/versions/15/behavior-changes-15
- https://developer.android.com/build/releases/agp-9-3-0-release-notes
- https://developer.android.com/build/migrate-to-built-in-kotlin
