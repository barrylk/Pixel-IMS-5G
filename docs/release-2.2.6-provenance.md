# 2.2.6 source reconciliation

The 2.2.5 test build and public 2.2.1 release developed separately from commit `4eddb9e`. The numerical version order did not imply that either contained the other's fixes.

## Inputs

- Local 2.2.5 source: preserved in commit `4654f5a`, including all tracked modifications and untracked source/test files from the original working project.
- Installed 2.2.5 APK: version code 37; SHA-256 `d53663e5a0762972e567f6a08c99fe89c904eb224a1e8b98a04b33bfcd82456e`. Read back from the connected device and matched byte-for-byte to the saved root-verified APK.
- Public 2.2.1 source: GitHub tag `v2.2.1`, commit `bb0a4b2edff4880b98cc6622c5e89f87a29e3312`. Its reporting changes, seven regression tests and release notes were cherry-picked; both historical changelog entries were retained.

## Preserved 2.2.5 behavior

- Shell-resolved `su`, one in-flight root request queue and root-service startup through the verified shell.
- Clearing/restoring Binder identity around forwarded telephony transactions.
- KernelSU single-file boot bind, read-only remount with both source and target, firmware/hash/SELinux validation, namespace handling and rollback.
- Existing Magisk/APatch module paths and staged overlay labels.
- Live-patch status, revalidation, Setup readiness and overlay evidence in field-test reports.

## Imported 2.2.1 behavior

- Accurate callback registration failure and no-event summaries.
- Independent missing-serving-cell, conflicting-CellInfo and unmatched-physical-channel warnings.
- All seven original field-test reporting regression tests.

## Update identities

- Public release certificate SHA-256: `784be02ecb5b5144008282db33e61ba285dbfcd508ff834e0905f7941b3872c9`, enforced by the existing GitHub release workflow.
- Installed local 2.2.5 certificate SHA-256: `df4248821e082f0fd8190bf98ce492d3fd55c9fb1574d10a94457fe4cc08fc98`, matched to the local build key.

No device configuration change or fresh on-device Magisk/KernelSU acceptance test is implied by source reconciliation. Build/test results and APK signatures must be verified for the final artifacts.

## Local validation on 2026-10-22

- `gradlew --no-daemon ktlintCheck :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease`: BUILD SUCCESSFUL, including R8 and release vital lint. The configured root `ktlintCheck` reports no Kotlin script sources and is not evidence of app-wide Kotlin formatting coverage.
- 53 unit tests, zero failures and zero errors. Includes all seven public 2.2.1 reporting tests and the 2.2.5 root/Binder/mount tests.
- The new combined-release changelog regression was observed failing before the 2.2.6 entry was added and passing afterward.
- Core root and mount implementations have no diff from preserved 2.2.5 commit `4654f5a`; field-test changes against that commit match the imported 2.2.1 fix.
- Local optimized APK metadata: `com.nirmala.pixel5gims`, version `2.2.6`, code `38`, min SDK 31, target SDK 36.
- Local optimized APK signature verified against the installed 2.2.5 certificate. APK SHA-256: `c003d67d8b2a869e1166deb62e9f117aa3492ebdb38b4b00cad57c1c51a1e328`.
- The local Java process required a separate temporary socket directory to avoid a Windows loopback error; this setting is not part of the Android app or committed build configuration.
