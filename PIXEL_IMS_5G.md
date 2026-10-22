# Pixel IMS 5G technical overview

Current release: **2.2.6**, version code **38**, application ID **`com.nirmala.pixel5gims`**.

For installation, features and troubleshooting, use the [README](README.md). Historical changes are preserved in the [changelog](CHANGELOG.md).

## Privileged backends

The app uses Android telephony Binder interfaces through either a libsu root service or Shizuku. Root startup resolves `su` through the device shell, queues concurrent permission requests and launches the service using the verified shell. Forwarded Binder transactions clear and restore caller identity around the privileged operation.

Root manager permission remains a prerequisite. KernelSU, KernelSU Next, Magisk and APatch policy and firmware behavior must be checked on the actual device; build success alone does not establish runtime compatibility on every configuration.

## Regional modem database

The installer validates the current Tensor `cfg.db`, patches a copy and runs integrity checks before staging a reversible module. It does not write modem NV/EFS or spoof country identifiers.

- KernelSU uses a native boot-stage single-file bind of `/vendor/firmware/carrierconfig/cfg.db`, with firmware/hash checks, mount-namespace handling, copied SELinux labels, a read-only remount and rollback on failure. No mounting metamodule is needed for this module.
- Magisk and APatch retain the systemless module path, including staged overlay label handling.
- Setup distinguishes staged, active, stale and failed patches and offers firmware revalidation where appropriate.

A live matching database establishes patch state, not network acceptance of 5G. Follow the [release guide](docs/release-guide.md) to install, reboot and inspect the result.

## Field-test evidence

The 2.2.1 reporting fixes are included alongside the preserved 2.2.5 implementation:

- Callback registration failure is separate from successful registration without delivered events.
- Timed polling remains primary evidence when callbacks are unavailable.
- Missing selected-SIM serving cells, conflicting CellInfo and unmatched device-global physical channels receive independent warnings.
- A 5G icon or NR-availability flag alone is not active NR evidence.

See [source reconciliation](docs/release-2.2.6-provenance.md) for the input commits and validation, and [building](docs/building.md) for SDK, test, R8 and signing instructions.

## Origin and license

Developed by **Nadeeja Nirmala**. Based on [kyujin-cho/pixel-volte-patch](https://github.com/kyujin-cho/pixel-volte-patch), with modem database research credited in the [README](README.md#origin-and-license). Licensed under [GNU GPL v3](LICENSE). This project is unofficial and is not affiliated with Google or a mobile carrier.
