# WVC

WVC changes ringtone and notification volume when a selected Wi-Fi network is
nearby. It includes named sound profiles, background monitoring, and a light
Compose interface.

## Open and run in Android Studio

This branch targets Android 17. **Use Android Studio Quail 2 (2026.1.2) or a
newer version that supports AGP 9.3.** Quail 4 (2026.1.4) is the current stable
release as of October 2026.

| Component | Project version |
|---|---|
| Android Gradle plugin (AGP) | 9.3.0 |
| Gradle | 9.5.0, downloaded by the checked-in wrapper |
| Gradle JDK | 21, matching CI |
| Android SDK platform | Android 17 / API 37 (`platforms;android-37.0`) |
| SDK Build Tools | 36.0.0 |
| Minimum device version | Android 8 / API 26 |

1. Update Android Studio using **Help → Check for Updates** (on macOS,
   **Android Studio → Check for Updates**). If the updater does not offer a
   compatible release, [download the current stable installer](https://developer.android.com/studio).
2. Open the repository root containing `settings.gradle.kts`, on branch
   `fix/android-modern-background-monitoring`.
3. In **Settings → Build, Execution, Deployment → Build Tools → Gradle**, select
   **JDK 21** as the Gradle JDK. The bundled `jbr-21` is suitable; otherwise use
   the dropdown's **Download JDK** option. Use the project's Gradle wrapper.
4. In **Tools → SDK Manager**, install **Android 17 (API 37)**, **Android SDK
   Build-Tools 36.0.0** (enable **Show Package Details**), and **Android SDK
   Platform-Tools**. Accept the requested SDK licenses.
5. Select **File → Sync Project with Gradle Files**, choose the **app** run
   configuration and a device/emulator, then click **Run**.

Use a physical Android device to test nearby Wi-Fi scans, entering/leaving range,
and background behavior. An emulator is useful for checking the interface.

### “Latest supported version is AGP 8.8.0”

That message is the installed Android Studio's compatibility limit, not the
latest available AGP release. Update the IDE using the steps above. The
[official compatibility table](https://developer.android.com/studio/releases#android_gradle_plugin_and_android_studio_compatibility)
lists Quail 2 as supporting AGP 9.3.

AGP 8.8 officially supports only API 35. Changing just `agp` to `8.8.0` would also
conflict with this branch's Gradle version and built-in Kotlin configuration.
Keep the checked-in build versions together to retain the API 37 target.

## Build from a terminal

Set `JAVA_HOME` to JDK 21 and install the SDK packages above. Android Studio
normally creates `local.properties` with your local `sdk.dir`; command-line-only
setups can use `ANDROID_HOME` instead. Do not commit machine-specific paths.

Windows PowerShell:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

macOS / Linux:

```sh
bash gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.
GitHub Actions also uploads it with reports in the `android-check-results`
artifact of each successful **Android checks** run.

## Project notes

- [Android behavior, permissions, and device testing](docs/ANDROID_COMPATIBILITY.md)
- [Interface and native preview screenshots](docs/UI_REDESIGN.md)
- [AGP 9.3 build requirements](https://developer.android.com/build/releases/agp-9-3-0-release-notes)
- [AGP 8.8 build requirements](https://developer.android.com/build/releases/agp-8-8-0-release-notes)
