# Install and update to 2.2.6

[Download the public release APK](https://github.com/barrylk/Pixel-IMS-5G/releases/download/v2.2.6/Pixel-IMS-5G-v2.2.6.apk) · [Release notes](https://github.com/barrylk/Pixel-IMS-5G/releases/tag/v2.2.6)

2.2.6 combines the working 2.2.5 root compatibility build with the diagnostic fixes published separately in 2.2.1. Its version code is **38**, higher than both previous builds.

## Choose the correct APK

| Installed app | Update path |
| --- | --- |
| Public GitHub release, including 2.2.1 | Install `Pixel-IMS-5G-v2.2.6.apk` from the release page over the existing app |
| Locally signed 2.2.5 test build | Use a 2.2.6 build signed with the same local key; the public APK has a different certificate |
| No existing installation | Install the public release APK |
| Legacy `com.fibc.pixel5gims` installation | This older package predates the application-ID migration; the current app uses `com.nirmala.pixel5gims` |

Android requires matching signing certificates for an in-place update. An “App not installed” or package-conflict error can indicate a key mismatch; a higher version number cannot override it. A same-key update avoids uninstalling the current app and losing its app data.

## First launch

1. Install the APK and open **Pixel IMS 5G**.
2. Choose **Root** or **Shizuku**, then approve access in the corresponding manager.
3. Confirm that Setup shows the intended SIM. Start with automatic bands and a normal LTE + NR preference.
4. Review the Setup checks and use the offered action for the check that needs attention.

## KernelSU and KernelSU Next

Enable Superuser access for Pixel IMS 5G in the root manager. The regional patch uses a single-file boot bind of `/vendor/firmware/carrierconfig/cfg.db`; no mounting metamodule is required for this module.

If an old overlay is inactive or the firmware changed, select **Revalidate for this firmware**, review the confirmation and reboot after installation. The module validates firmware hashes and SELinux labels, mounts the file read-only and records boot failures. Confirm **Patch active** after reboot.

## Magisk and APatch

Approve root access in the manager. The app retains its systemless module installation path for these managers. Install or revalidate through the app's confirmation flow, reboot when requested and check the reported patch state.

## Shizuku

Start Shizuku and grant Pixel IMS 5G permission. This backend can apply supported Android-side IMS and radio settings. Installing or mounting the vendor modem database requires Root and a supported module manager.

## Verify the result

Use Network monitoring or the standard field test with mobile data enabled. A 5G icon, an NR-availability flag or a mounted database alone does not establish active 5G. Look for connected NR state or serving NR evidence; the report distinguishes missing observations from contradictory or cross-SIM data.

For a root failure, check the manager's Superuser policy first. For an inactive patch, inspect the app's boot-mount status and firmware-revalidation action. If a radio setting removes service, restore automatic bands or use the app's Undo/recovery controls.

## Public APK verification

The published 2.2.6 APK was downloaded and checked after publication:

| Field | Value |
| --- | --- |
| Package | `com.nirmala.pixel5gims` |
| Version | `2.2.6` |
| Version code | `38` |
| Minimum Android API | `31` |
| Target Android API | `36` |
| Size | `3,835,036` bytes |
| SHA-256 | `988c9f996b8ca857a571218fa7aefe0395a51f6cae5608dedcde757b39d337aa` |
| Signing certificate SHA-256 | `784be02ecb5b5144008282db33e61ba285dbfcd508ff834e0905f7941b3872c9` |

The optimized release build, debug build and all 53 unit tests passed. Preserving the working 2.2.5 implementation and passing tests do not replace a fresh runtime check on each device, root manager and firmware combination.
