# Automatic APK builds and releases

You can download APKs from GitHub without building the app in Android Studio.

| Trigger | Result |
|---|---|
| Push a branch or open/update a pull request | **Android checks** runs tests/lint, builds debug and unsigned release variants, and uploads the installable debug APK in `android-check-results` |
| Manually run **APK release** | Tests/lint, a signed release APK, and a SHA-256 checksum as workflow artifacts |
| Push a version tag, such as `v1.1` | The same validated, signed APK and checksum attached to a new GitHub Release |

## One-time signing setup

Signed builds need the following **repository Actions secrets** in
**Settings → Secrets and variables → Actions → New repository secret**:

| Secret | Value |
|---|---|
| `WVC_RELEASE_KEYSTORE_BASE64` | Base64-encoded contents of your `.jks` / `.keystore` file |
| `WVC_RELEASE_STORE_PASSWORD` | Keystore password |
| `WVC_RELEASE_KEY_ALIAS` | Alias of the app signing key in that keystore |
| `WVC_RELEASE_KEY_PASSWORD` | Password of that key, which may equal the keystore password |

Use the **same app signing key used for your existing distributed APKs**, so the
new APK can update those installations. A Play upload key and a Play-managed app
signing key may differ. Debug APKs also have a different signing key; installing a
release over a debug installation may require uninstalling the debug app first,
which removes its saved profiles.

If this app has never had a release signing key, create one in Android Studio:
**Build → Generate Signed App Bundle / APK → APK → Create new**. Keep the keystore
and passwords backed up outside the repository. The workflow never generates a
replacement key and stops with a clear error if any signing secret is missing.

On Windows, copy your keystore's Base64 value to the clipboard with PowerShell,
then paste it into the `WVC_RELEASE_KEYSTORE_BASE64` secret:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("C:\path\to\wvc-release.jks")) | Set-Clipboard
```

With the GitHub CLI on macOS/Linux, you can send it directly into the secret:

```sh
base64 < /path/to/wvc-release.jks | tr -d '\n' | gh secret set WVC_RELEASE_KEYSTORE_BASE64 --repo mfcc168/WVC
```

Keep the key and its Base64 value out of commits and chat messages. The workflow
restores the key in the runner's temporary directory, removes it after the build,
and uploads only the APK, checksum, and validation reports. Signing secrets are
not provided to pull-request builds. No personal access token is needed for
publishing; the publish job uses GitHub's built-in token.

## Publish a version

1. Merge the workflow branch into `main` when the app is ready. For every later
   release, update `versionName` and increase `versionCode` in
   `app/build.gradle.kts`, then commit and push those changes.
2. Tag that commit using `v` followed by its exact `versionName`. The current
   version is `1.1` (version code `2`), so its matching tag is `v1.1`:

   ```sh
   git switch main
   git pull --ff-only
   git tag v1.1
   git push origin v1.1
   ```

3. Watch **Actions → APK release**. After tests, release lint, signature checks,
   and APK verification pass, **Releases** contains `WVC-1.1.apk` and
   `SHA256SUMS.txt`, with generated release notes.

For example, a later `versionName = "1.2.0"` with `versionCode = 3` uses tag
`v1.2.0`. A suffix such as `1.2.0-beta.1` creates a GitHub prerelease. Tags must
match the built APK version exactly. Do not reuse or move an existing version tag.

The release is kept as a draft while files upload and published only after the
upload succeeds. Rerunning a failed upload can finish that draft; a published
release's assets are left unchanged. A tag must point to a commit containing
`.github/workflows/apk-release.yml`.

## Build manually without publishing

After the workflow exists on the default branch, open **Actions → APK release →
Run workflow**, choose a branch or tag, and run it. Manual runs need the same
signing secrets and produce a `wvc-release-<run ID>` artifact, retained for 30
days. They do not create a public release. The build summary shows the APK
version and version code.

For testing before signing is configured, open a successful **Android checks**
run and download **android-check-results**. Extract `app-debug.apk` from the
artifact. This is the debug build, not the signed release build.

## Local builds

With no signing environment variables, `bash gradlew :app:assembleRelease`
builds `app-release-unsigned.apk`; it is not directly installable. To sign locally,
set `WVC_RELEASE_KEYSTORE_PATH` to your keystore's absolute path and set the three
password/alias environment variables listed above before building. A partially
configured signing environment is rejected. Keep passwords out of build files.

## References

- [Android signing and updates](https://developer.android.com/studio/publish/app-signing)
- [GitHub Actions secrets](https://docs.github.com/en/actions/how-tos/write-workflows/choose-what-workflows-do/use-secrets)
- [Manual workflow availability](https://docs.github.com/en/actions/reference/workflows-and-actions/events-that-trigger-workflows#workflow_dispatch)
