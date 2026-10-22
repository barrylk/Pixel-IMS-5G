# Pixel IMS 5G — English documentation

The current English guide is maintained in [README.md](README.md).

**Current public release: 2.2.6, version code 38.** This release combines the working 2.2.5 root compatibility build with the field-test reporting fixes from 2.2.1.

- [Download the release APK](https://github.com/barrylk/Pixel-IMS-5G/releases/download/v2.2.6/Pixel-IMS-5G-v2.2.6.apk)
- [Features, setup and troubleshooting](README.md)
- [Install and update guide](docs/release-guide.md)
- [Build and signing guide](docs/building.md)
- [Complete changelog](CHANGELOG.md)

The Android package is `com.nirmala.pixel5gims`. KernelSU / KernelSU Next use a single-file modem mount without a mounting metamodule; Magisk and APatch retain their existing module paths. Shizuku supports Android-side settings and cannot mount the vendor database.

Locally signed test builds require the same signing key for an in-place update. See the update guide before replacing a local 2.2.5 installation with a public APK.
