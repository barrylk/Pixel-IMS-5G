# Pixel IMS 5G

An experimental Shizuku-powered IMS and radio configuration app for Google Tensor Pixel phones, developed and tested on a Pixel 7 Pro running Android 17.

## Features

- Enable VoLTE, VoNR, VoWiFi, NSA and SA carrier configuration.
- Apply NSA/LTE+NR preference or experimental SA/NR-only mode.
- Show live serving radio, LTE/NR bands, NR advertisement and EN-DC eligibility.
- Actively refresh cell measurements and infer omitted bands from EARFCN/NR-ARFCN.
- Request LTE/NR band restrictions and detect when Pixel firmware rejects them.
- Read the Tensor modem's LTE carrier-aggregation enablement status.
- Material 3 Expressive interface with dynamic Pixel colors and glass surfaces.
- Check GitHub Releases and download signed in-app updates.

## Install

1. Install and start [Shizuku](https://shizuku.rikka.app/).
2. Download the latest APK from [Releases](https://github.com/barrylk/Pixel-IMS-5G/releases/latest).
3. Install Pixel IMS 5G and grant its Shizuku permission.

Changing radio modes can remove calls, SMS, or data. The app can change modem preferences and carrier configuration, but cannot create network coverage, EN-DC support, carrier authorization, or SA registration.

## Developer and support

Developed by **Nadeeja Nirmala**.

- [GitHub](https://github.com/barrylk)
- [Facebook](https://www.facebook.com/nirmalafromslk/)
- [Bug reports and feedback](https://github.com/barrylk/Pixel-IMS-5G/issues)

## Origin and license

Pixel IMS 5G is based on the original GPL project [kyujin-cho/pixel-volte-patch](https://github.com/kyujin-cho/pixel-volte-patch) and the work of its community contributors. This modified project remains licensed under the GNU General Public License v3.0. See [LICENSE](LICENSE).

This is an unofficial community project and is not affiliated with Google or any mobile carrier.

## Band scan limitations

The app requests a fresh cell-information measurement and lists every LTE/NR band the Android radio layer returns. When the radio omits a band but exposes an EARFCN or NR-ARFCN, the app derives the band using Android's radio-frequency mapping. No Android app can display a transmitter the modem does not measure or deliberately withholds from the framework.
