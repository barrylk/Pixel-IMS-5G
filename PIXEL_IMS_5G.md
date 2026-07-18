# Pixel IMS 5G

Pixel IMS 5G is an experimental, Shizuku-powered Android app for Google Tensor Pixels. This build is tested on a Pixel 7 Pro running Android 17.

Version 0.5 introduces a Material 3 Expressive interface with Pixel dynamic colors, edge-to-edge layout, translucent rounded cards, layered tonal light, and a floating glass navigation dock. Light and dark appearances follow the phone automatically.

## Features

- Select NSA only, SA only, NSA + SA, or disabled carrier configuration per SIM.
- Choose NSA/5G preferred while retaining LTE fallback.
- Choose experimental SA/NR-only mode.
- Restore the user radio mask that was active before the app changed it.
- Enable VoLTE and VoNR and restart IMS registration.
- Retain the upstream Pixel IMS carrier-config editor and diagnostics.
- Apply per-SIM LTE and NR band restrictions from a dedicated Bands tab.
- Read the Tensor modem's OEM LTE carrier-aggregation enablement status.
- Verify band restrictions after applying them and report when the modem rejects them.
- Show serving and nearby-cell LTE/NR bands reported by the modem.
- Distinguish NR advertisement, EN-DC/NSA eligibility, and active SA registration.
- Apply one-tap NSA-only/LTE+NR preference or experimental SA/NR-only mode on Android 17.
- Contact the developer and report feedback from the in-app About screen.
- Check GitHub Releases and download signed APK updates from inside the app.
- Reset band selection to automatic at any time.

## Install and use

1. Install and start Shizuku using Wireless debugging or ADB.
2. Install the APK and open **Pixel IMS 5G**.
3. Grant the Shizuku permission.
4. Open the SIM tab.
5. Set **5G NR architecture** to **NSA + SA** and enable **VoLTE**.
6. Set **Preferred radio mode** to **NSA/5G preferred (LTE + NR)**.
7. Restart IMS registration or reboot the phone if IMS does not register immediately.

The **Bands** tab accepts comma-separated LTE and NR band numbers. Selecting several LTE bands requests eligible carrier-aggregation candidates, but cannot force a specific CA combination; that decision remains with the modem and network. Pixel 7 Pro firmware on Android 17 may accept the standard Android request and then discard it. The app detects this and reports that the modem remains on Automatic. Always use **Automatic bands** before travelling or when service disappears.

The detected-band list contains only cells the modem currently reports; it is not a complete spectrum scan. **Force NSA preference** enables the NSA carrier profile and allows LTE+NR. **Force SA-only** permits only NR and is deliberately disruptive. Neither mode can make a network accept registration or supply EN-DC on a cell where the carrier has disabled it.

Band diagnostics request a fresh modem measurement. If Android omits a band number but exposes the LTE EARFCN or NR-ARFCN, the app derives the operating band using the platform frequency mapping.

NR-only mode can leave the phone without calls, SMS, or data when standalone 5G is unavailable. It does not create coverage, bypass a carrier IMEI allowlist, or guarantee IMS registration. Carrier and network support are still required.

## Build

The project compiles with Android SDK 36 and JDK 17 or newer. It needs a full/patched Android 36 `android.jar` because it calls hidden telephony binder interfaces. Place that jar at `platforms/android-36/android.jar`, set `sdk.dir` in `local.properties`, and run:

```powershell
.\gradlew.bat assembleDebug
```

## Origin and license

Developed by **Nadeeja Nirmala** — [GitHub](https://github.com/barrylk) · [Facebook](https://www.facebook.com/nirmalafromslk/) · [Issues and feedback](https://github.com/barrylk/Pixel-IMS-5G/issues).

This project is a modified version of [kyujin-cho/pixel-volte-patch](https://github.com/kyujin-cho/pixel-volte-patch), based on commit `0b4b5fef31e4e4904eece60bdb360ea3111ac3aa`. Modifications add radio-mode control, 5G carrier configuration, a unique application ID, and offline version display.

The complete project remains licensed under GNU GPL v3. See `LICENSE`. Pixel is a trademark of Google LLC; this project is unofficial and is not affiliated with Google or any carrier.
