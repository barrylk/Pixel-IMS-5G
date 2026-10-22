# Changelog

### 2.2.6 - combined root compatibility and field-test fixes

- Combines the working 2.2.5 root build with all diagnostic fixes from the public 2.2.1 release; version code 38 upgrades both build lines.
- Preserves shell-resolved root startup, queued permission requests and Binder identity handling for KernelSU and Magisk.
- KernelSU binds only `cfg.db` at boot without a metamodule, with hash/SELinux checks, a read-only remount and rollback on failure. Magisk and APatch keep their existing installation paths.
- Setup distinguishes active, staged and stale patches and supports firmware revalidation.
- Field-test reports distinguish callback registration failure from no delivered events, and separate missing serving cells, conflicting CellInfo and unmatched device-global physical channels.
- See the [release notes](.github/release-notes/v2.2.6.md) for update signing and revalidation instructions.

### 2.2.0 - liquid glass, the modem patch up front, and issue fixes

- **Liquid glass UI** in light and dark, with an in-app System / Light / Dark switch in the top bar.
- **The 5G regional modem patch leads Setup** as soon as root is granted, with its prerequisites checked and one install button. Every install goes through the confirmation dialog.
- **KernelSU and APatch** can install the modem patch, not only Magisk ([#13](https://github.com/barrylk/Pixel-IMS-5G/issues/13)).
- **Reapply after reboot** for Shizuku mode, opt-in on SIM Config ([#6](https://github.com/barrylk/Pixel-IMS-5G/issues/6)).
- **Fixed:** the app crashing after applying a setting on Android 17 QPR2 ([#7](https://github.com/barrylk/Pixel-IMS-5G/issues/7)), and settings wrongly reported as "did not accept" ([#14](https://github.com/barrylk/Pixel-IMS-5G/issues/14)).
- **Fixed:** the modem patch failing with "Unable to find app for caller" ([#15](https://github.com/barrylk/Pixel-IMS-5G/issues/15)); install errors now show on the card.
- **Fixed:** NSA 5G reported as not connected; root mode passing the access check through a Shizuku grant; pages stuck on "no SIM" when SIMs loaded late; buttons left disabled after rotating mid-action.

### [2.1.0](https://github.com/barrylk/Pixel-IMS-5G/releases/tag/v2.1.0) - task-first setup

**The app opens on the question you actually have.** Every version until now opened on a settings list,
which assumes you already know which of twenty switches is holding 5G back.

- **Setup screen.** Runs the checks that have to pass for 5G on this SIM, in dependency order, and names
  the single one that is blocking: privilege, IMS support, VoLTE, NR architecture, band selection, VoNR,
  the Magisk modem region patch, whether an NR cell is actually registered, and whether IMS registered.
- **One-tap fixes.** Each failing check carries its own action - enable VoLTE, set NR to NSA + SA, enable
  VoNR, apply the modem patch, restart IMS, or jump to band selection.
- **Outcomes, not just settings.** The last two checks report what the radio is actually doing, so the
  screen ends with evidence rather than intent.
- **Nothing was removed.** Every individual switch is one tap away under *Expert settings*, and the
  previous overview keeps its own route.

**The panels are visible now.** 2.0's glass was ported from a web prototype where every panel also had
`backdrop-filter: blur()` behind it. Android has no backdrop blur, and the alpha values came across without
it - a 7% white fill over a near-black ground lands about ten levels above the background, which is an
outline, not glass. The fill now carries roughly three times the alpha, the ground is lifted off the void
so panels have something to sit above, and a one-pixel sheen along the top edge stands in for the highlight
the blur used to give.

### [2.0.0](https://github.com/barrylk/Pixel-IMS-5G/releases/tag/v2.0.0) - live network monitor, new interface

**Network monitoring.** The app could already read every field a cell monitor shows, but only as a one-shot
snapshot taken when a page happened to open. 2.0 turns that into an actual monitor.

- **Live sampling.** A new Network tab samples the radio every three seconds. Serving cell with the full
  parameter set, the LTE anchor when the phone is on NSA, and every cell in view sorted by strength.
- **Signal history.** A rolling plot of the last ninety RSRP samples with min, mean and max. A dropout has a
  shape, and the shape is the diagnosis.
- **Cell log.** Handover, band change, NR leg up and down, service lost, and drops of 12 dB or more - each
  timestamped. Only real transitions are logged; a log that records every sample is a log nobody reads.
- **Parameter grid.** PCI, EARFCN / NR-ARFCN, TAC, cell ID, RSRQ, SINR, CSI-RSRP, CSI-SINR, RSSI, CQI,
  bandwidth and timing advance.
- **Nothing was removed.** The attach trace, physical channels, TelephonyRegistry log and CarrierConfig diff
  keep their own route, reachable from Network under *Deep diagnostics*.

**Interface.** Rebuilt again, this time with depth.

- A lit navy-teal ground with frosted panels over it. 1.0.9 was flat and neutral; translucency needs something
  to be translucent over, which is what the original glass theme lacked.
- An animated swept-arc gauge for the headline reading, with the quality ramp driving its colour.
- **Chakra Petch** and **JetBrains Mono** now ship with the app, so display type and measurements have real
  faces instead of whatever the device happened to substitute.
- Corner radii return to 18-26dp, and the navigation bar floats again - as frosted glass with a lit edge,
  matching the panels rather than the opaque pill of earlier versions.
- Instrument tokens (panel fill, hairline, the good/fair/poor ramp) live in their own theme layer rather than
  being squeezed into approximate Material roles.

### [1.0.9](https://github.com/barrylk/Pixel-IMS-5G/releases/tag/v1.0.9) - new interface

- **A completely new look.** The violet/blue/cyan glassmorphism is gone: no gradient backdrop, no
  colour blobs behind the content, no translucent cards. The app now uses a neutral instrument
  palette — grey carries the structure, one blue is the accent, and colour is spent only on signal
  state, so a green reading means something.
- **Readings are monospaced.** Band numbers, physical-channel captures, diagnostic values, the
  TelephonyRegistry event log and root evidence use a fixed-width face, so digits keep their columns
  as a live measurement updates.
- **Organised, not cluttered.** Property rows no longer each draw their own floating card. Related
  controls are grouped into panels with hairline dividers — SIM Config is now Network, Calling,
  Status bar, quick tiles and miscellaneous rather than one undifferentiated scroll.
- **Navigation bar redesigned.** The floating 28dp rounded pill with its shadow and spring-animated
  icons is now a flush bar with a hairline above it, with selection shown by colour alone.
- **Better light mode.** Signal colours are tuned per theme instead of one value serving both, and
  four hard-coded greens that were never in the palette are gone.
- **Pages open faster.** The page-header entrance animation is removed; it delayed the first reading
  by roughly 400 ms on every page open.
- Corner radii top out at 16dp, where the old scale began. Dynamic colour is deliberately not offered:
  the palette carries meaning, and wallpaper-derived hues would decide the signal colours.

### [1.0.8](https://github.com/barrylk/Pixel-IMS-5G/releases/tag/v1.0.8) - honest SIM Config controls

- **Switches tell the truth.** Every SIM Config control now writes, reads the value back, and only
  then settles. A setting the modem refuses reports why, instead of quietly moving to the position
  you asked for. This is the same class of problem as the `1.0.6` fix, one layer up.
- **No more freezing on toggle.** Privileged writes no longer run on the interface thread. Toggling
  a setting cannot hang the app or trip an ANR while the root or Shizuku call is out.
- **Settings survive rotation.** The page state moved into a `ViewModel`, so rotating the phone or
  switching light/dark no longer throws away the page — or a change still being applied.
- **Tidier page.** Controls are grouped into Network, Calling, and Status bar sections rather than one
  undifferentiated scroll, and each shows an *Applying…* state while its write is in flight.
- **Faster load.** The page reads the carrier configuration once instead of roughly thirty times, and
  the reflection scan over every `CarrierConfigManager` field is gone — its result was never used.
- The privileged operations that pause mid-sequence (easy mode, root force, Google-defaults restore,
  the Shizuku regional patch, Tensor CA, undo) are now `suspend`, so the compiler enforces that they
  stay off the main thread rather than each caller having to remember.
- **Please re-check your settings after updating.** Every SIM Config control was rewritten here. Open
  SIM Config once and confirm your VoLTE, VoNR, and band choices still read correctly.

### [1.0.7](https://github.com/barrylk/Pixel-IMS-5G/releases/tag/v1.0.7) - internal cleanup

- **Much smaller download.** Code shrinking (R8) and resource shrinking are now enabled, and fourteen unused libraries have been removed. The release package drops from about 54 MB to roughly 3 MB.
- **No feature changes.** Root and Shizuku behaviour, band selection, the regional modem patch, and field test are all unchanged. Nothing about the 5G workflow is different.
- **Automated build checks.** Every push is now compiled, linted, and unit tested on GitHub before a release can be cut.
- One test-only dependency was added so unit tests keep a real `org.json` implementation on the JVM.

### [1.0.6](https://github.com/barrylk/Pixel-IMS-5G/releases/tag/v1.0.6) - small bug fixes

- Fixed VoNR, VoWiFi, enhanced-data-icon, and other Root SIM controls replacing
  one another when changed separately.
- Root mode now saves one complete CarrierConfig profile per SIM and restores
  it after reboot, user unlock, app update, or Root service reconnect.
- **Restore Google defaults** also clears the saved Root profile.
- Shizuku remains session-only and may reset after reboot, as intended.

### [1.0.5](https://github.com/barrylk/Pixel-IMS-5G/releases/tag/v1.0.5) — small update

- Added the **VoLTE Fix**: Root mode now displays a compact boxed VoLTE badge
  in Android's right-side status area only while IMS is registered over LTE or
  NR.
- Added a separate live VoWiFi indicator that appears only for real IMS over
  IWLAN. Neither indicator creates an ongoing notification.
- Added a reversible, per-SIM **Root VoWiFi repair** that opens Android WFC
  gates, selects Wi-Fi preferred, restarts IMS, and reads the result back.
- Added actionable VoWiFi diagnostics for missing IMS data profiles, ePDG
  timeouts, DNS failures, and carrier IKE authentication rejection.
- Expanded field reports with the effective VoWiFi setting, IMS transport,
  IWLAN state, Wi-Fi state, and sanitized recent failure evidence.

### [1.0.4 rev](https://github.com/barrylk/Pixel-IMS-5G/releases/tag/v1.0.4r)

- Added prominent, step-by-step **How to enable 5G** instructions inside the
  app for Root and Shizuku users.
- Documented the complete Root path: Automatic bands, Force NSA, Root Force,
  Magisk regional modem compatibility install, reboot, and field verification.
- Documented the complete Shizuku path: Automatic bands, Force NSA, Easy Mode,
  reversible regional profile, Monitor, and Field Test.
- Clarified that Shizuku opens Android-side gates but cannot replace Tensor
  `cfg.db`; modem-policy rejection may still require the Root/Magisk patch.
- Fixed OTA comparison so `1.0.4r` is recognized as newer than `1.0.4`.

### [1.0.4](https://github.com/barrylk/Pixel-IMS-5G/releases/tag/v1.0.4)

- Added a versioned liquid-glass **What’s New** experience after OTA updates,
  with semantic colors highlighting features, improvements, fixes, and
  important limitations. The current changelog can be reopened from **About**.
- Persisted the GitHub target version and release notes across Android’s
  installer, with an embedded structured fallback for sideloaded or interrupted
  updates.
- Redesigned the complete interface with premium typography, calmer spacing,
  semantic radio-status colors, refined glass surfaces, and restrained motion.
- Replaced the Material bottom navigation with fixed, perfectly centered icon
  slots and stable labels, and added the **by Nirmala** app signature.
- Added reversible Wi-Fi isolation for Monitor and Field Test, with fail-closed
  behavior and automatic restoration.
- Expanded reports with OS/build identity, operational PLMN, evidence
  provenance, NR/EN-DC semantics, IMS/callback coverage, readable policy gates,
  and sanitized root modem deltas.
- Added an honest regional-5G Shizuku limitation notice for stock Pixel OS and
  alternative Pixel operating systems.

### [1.0.3](https://github.com/barrylk/Pixel-IMS-5G/releases/tag/v1.0.3)

- Added automatic GitHub release checks at startup and periodically in the
  background.
- Added a polished **New version x.x.x is available** Android notification,
  shown once for each newer release.
- Added an **Update now** notification action that opens the in-app GitHub
  updater directly.
- Added live download percentage and pending, paused, completed, and failed
  states inside the app.
- Added a secure installer handoff after the signed APK finishes downloading,
  including Android's unknown-app-source permission flow when required.
- Preserved the established package name and OTA signing identity, allowing
  existing installations to update without uninstalling.

### [1.0.2r](https://github.com/barrylk/Pixel-IMS-5G/releases/tag/v1.0.2r)

- Reorganized the app into **Home**, **Controls**, **Monitor**, and
  **Field Test** so IMS/5G and band controls are easier to find.
- Restored the complete IMS, 5G, LTE+, carrier aggregation, radio-profile, and
  LTE/NR band-selection controls in their dedicated sections.
- Added standard and Aggressive 5G field-test modes with detailed evidence
  capture, small connectivity checks, automatic restoration, and sanitized
  report export.
- Prevented Pixel 6 band-page crashes when Android rejects system-selection
  channel readback. Callback-confirmed selection remains usable on supported
  Android 16 and Android 17 builds.
- Made Shizuku Automatic Bands failures non-fatal and clearly report modem or
  Android restrictions instead of closing the complete regional profile.
- Added the in-app **How to use** guide and updated interface screenshots.

### [1.0.1](https://github.com/barrylk/Pixel-IMS-5G/releases/tag/v1.0.1)

- Added the reversible Shizuku regional compatibility profile for Android 16
  and Android 17.
- Added verification of effective NR modes and network gates instead of
  treating a successful Binder call as proof that the modem accepted a change.
- Added documented Dialog Sri Lanka n78 NSA field results and a real-world
  speed-test example.
- Clarified which Android-side VoLTE, VoWiFi, LTE+, band, and field-test
  functions work with Shizuku and which Tensor modem changes remain root-only.

### [1.0.0](https://github.com/barrylk/Pixel-IMS-5G/releases/tag/v1.0.0)

- Promoted the project to its first stable release under
  `com.nirmala.pixel5gims`.
- Expanded the privacy-labelled 5G field-test dump with NR frequency,
  registration, CarrierConfig, callback, cell, IMS, and sanitized privileged
  evidence.
- Added the validated, reversible Magisk Tensor regional modem compatibility
  patch with schema checks, database integrity verification, backup, and
  restoration safeguards.
- Added signed GitHub release APK support for future in-app updates.

> **Upgrade note:** version `0.12.6` changed the application ID to
> `com.nirmala.pixel5gims`, so users of the older package had to uninstall it
> once. Updates from `0.12.6` onward, including `1.0.5`, install normally.
