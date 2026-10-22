# Build Pixel IMS 5G

The current release is **2.2.6**, version code **38**, with application ID `com.nirmala.pixel5gims`.

## Requirements

- JDK 17, matching GitHub CI.
- Android SDK platform 36 and build tools 36.0.0.
- The full Android 36 API jar used by this project's workflows: [Reginer/aosp-android-jar](https://github.com/Reginer/aosp-android-jar/blob/main/android-36/android.jar).

Replace the SDK's `platforms/android-36/android.jar` with the full API jar. The stock SDK jar does not expose the hidden telephony APIs used by the app. Keep a copy of the stock jar if this SDK is shared with other projects.

Set your SDK location in an untracked `local.properties` file:

```properties
sdk.dir=C:/path/to/android-sdk
```

## Validate and build

On Windows:

```powershell
.\gradlew.bat --no-daemon ktlintCheck :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease
```

On Linux or macOS:

```sh
./gradlew --no-daemon ktlintCheck :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease
```

Outputs:

| Variant | Output | Purpose |
| --- | --- | --- |
| Debug | `app/build/outputs/apk/debug/app-debug.apk` | Local development; uses the local debug signing identity |
| Release | `app/build/outputs/apk/release/app-release-unsigned.apk` | Optimized with R8 and resource shrinking; requires signing before installation |

The configured root `ktlintCheck` task currently has no Kotlin script sources; passing it does not establish app-wide Kotlin formatting coverage. Unit tests exercise report warnings, root-request handling, Binder identity, patch readiness and the generated boot script's validation and rollback paths.

## Public release signing

The [release workflow](../.github/workflows/release.yml) builds the release variant using the existing GitHub signing secret and verifies the certificate before publication. A `v*` tag triggers publication with notes from `.github/release-notes/<tag>.md`.

Public certificate SHA-256:

```text
784be02ecb5b5144008282db33e61ba285dbfcd508ff834e0905f7941b3872c9
```

Keep signing keys out of the repository. A newly generated debug key cannot update an APK signed with the public release key. See the [update guide](release-guide.md) for the separate local 2.2.5 test-build update path.

The 2.2.6 source reconciliation and local test results are recorded in [release provenance](release-2.2.6-provenance.md).
