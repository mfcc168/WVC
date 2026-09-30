# WVC · white interface

The app has two tabs, **Home** and **Profile**, each with an icon. The same bottom
navigation stays available on phones and tablets.

## Home

- A large, sculpted Wi-Fi indicator with three gently expanding waves.
- Explicit monitoring status and Start/Stop/Resume controls. The waves represent
  enabled monitoring, not a promise of continuous or successful radio scans.
- A profile picker directly on Home. Selection closes the picker and updates the
  saved active profile; the service applies volumes after a fresh scan.
- In-range and out-of-range ringtone/notification summaries, with an edit action.
- A setup button opens permission readiness, Android settings links, and
  reboot/battery guidance without adding a third tab.

## Profile

Create, edit, rename, select, and delete saved sound profiles. The editor includes
an exact Wi-Fi name and separate ringtone/notification levels in and out of range.

Edits remain drafts until Save. Duplicate or blank names and invalid SSIDs show
inline errors. Drafts survive recreation, and modified drafts require confirmation
before discarding. Deleting the selected profile requires confirmation and stops
monitoring. Legacy profiles and saved settings are retained.

## Visual and motion design

The app intentionally stays light, including when Android uses dark mode. Soft
white surfaces, opposing diffuse shadows, charcoal controls, and restrained sage
status accents replace the blue theme. All shadows use ordinary drawing primitives
supported from Android 8, without software rendering or bitmap assets.

Tab changes crossfade, profile summaries resize smoothly, and the Home profile
control responds to a press. Wi-Fi waves run only while monitoring is enabled,
Home is visible, no overlay is open, and the activity is resumed. They stop when
Android's **Remove animations** setting is enabled, including live changes. Other
Compose animations follow the system duration scale.

Content stacks on small windows and enlarged text. Wider windows show Home in two
columns and paired profile cards. The editor scrolls independently of its fixed
Save/Cancel actions. System bars use dark icons to match the white appearance.
Text labels, selected tab/radio semantics, meaningful icon descriptions, and
48 dp or larger action targets remain available to accessibility services.

## Native previews

Generated from real Compose views with Robolectric native graphics and sample
profiles. These are not physical-device screenshots.

| Home | Profiles | Editor |
| --- | --- | --- |
| ![Home](screenshots/phone-home.png) | ![Profiles](screenshots/phone-profiles.png) | ![Editor](screenshots/phone-editor.png) |

![Tablet Home, with Android dark mode enabled](screenshots/tablet-home.png)

![Compact screen, 160% text, setup dialog](screenshots/compact-large-text-setup.png)

## Checks

Run with JDK 21:

```sh
bash gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

All 24 tests pass (14 UI, 6 storage/validation, 3 presence, and the existing sample).
Lint reports zero errors. The UI tests cover a 390 dp phone, a 1040 dp tablet with system dark mode, and a
320 dp window at 160% text size. They exercise profile selection from Home,
creation/editing, validation, restored drafts, discard/delete confirmation, both
tabs, setup access, and Stop with missing permissions. A frame comparison verifies
that Wi-Fi waves move while enabled and settle when paused.

GitHub's SDK setup explicitly requests `platform-tools`: the action's old default
also requested Google's retired `tools` package and failed before compilation.
Command-line tools are pinned to the same version used for local validation.

Physical-device IME, TalkBack, overnight Wi-Fi scanning, and reboot checks remain
in the [compatibility checklist](ANDROID_COMPATIBILITY.md).
