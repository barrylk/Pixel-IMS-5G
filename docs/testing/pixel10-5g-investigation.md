# Pixel 10 Pro XL: 2.2.7 Liquid investigation

Status: evidence captured; active NR attachment remains unresolved. Do not describe
this device as having a verified 5G fix or publish 2.2.7 on that basis yet.

## Device and evidence

On 2026-10-29, a rooted Pixel 10 Pro XL (`mustang`, hardware SKU `G45RY`) was
inspected through a serial-pinned remote ADB server. The reported baseband was
`g5400i-260604-260807-B-16035863`. Hardware SKU was also checked against bootconfig.

The private local capture contains:

- Complete carrier configuration database, manifests and configuration sequences;
  installed regional module and its boot/mount metadata.
- Original stock `cfg.db`, recovered by reading the relevant blocks of the
  read-only vendor ext4 filesystem. No unmount, filesystem write or flash was used.
- Full modem firmware image directory, RF configuration archives and hardware
  selection JSON; 1,398 UE capability protobuf files plus directory metadata.
- Live NV normal/protected files and radio configuration/loader files. These are
  live diagnostic snapshots, not an atomic partition backup or a validated restore
  image.
- Repeated telephony registry observations; full phone service and CarrierConfig
  dumps; radio/system logs; connectivity, settings, power, thermal, mount/process
  evidence; a complete system bug report and the installed public APK.

Archives were parsed and hashes compared with the staged phone copies. The
original database's SHA-256 matched the installed module's recorded source hash.
The radio archive skipped an unsupported socket file; the required NV files,
radio loader and library were individually confirmed present. An earlier truncated
firmware stream was superseded by the verified full transfer.

Raw archives, NV, bug reports and third-party reference clones stay in the ignored
local `build/pixel10-capture/` directory. They are not public release assets.

## Findings

1. The regional `cfg.db` bind is active and read-only. The modem process namespace
   sees the same patched database bytes as the module. This rules out a missing
   mount in this capture; it does not establish which capabilities the modem
   advertised over the air.
2. Stock versus installed database comparison found only intended `confmap`
   changes: wildcard `0` and PTCRB `20001` map to `it_iliad`, and `20005` was added
   with the same manifest. Other tables were unchanged. Both databases passed
   SQLite integrity checking. Configuration sequence content hashes also passed.
3. Dialog's Android SIM carrier ID is `1599`, while the modem configuration
   carrier ID is `0`. These are different namespaces; substituting the Android
   carrier ID into modem selection without evidence is not a fix.
4. Current captures show LTE data service and LTE physical carriers, with no
   active NR carrier. Android reports `isNrAvailable=true`,
   `isEnDcAvailable=false`, and `isDcNrRestricted=false`. NR signal readings are
   unavailable. Android 17 redacts some registry fields, including textual
   `nrState`; a redacted field must not be interpreted as numeric zero.
5. Current physical LTE channels can differ from retained ServiceState bandwidth
   summaries and older CellInfo. Current physical components take priority in the
   monitor; retained summaries remain explicitly separate.
6. Captured generic European/APAC capability files for the researched G45RY
   selector contain n78 combinations with LTE B1 and B3. The exact runtime
   capability file chosen by the modem was not established. Presence of those
   files does not prove their use or network acceptance, and is not a reason to
   replace every capability file with a donor profile.

The earlier same-SIM iPhone comparison remains relevant, but it was not repeated
in this capture window. Current LTE anchor observations must not be assumed to
be identical to those in the earlier screenshot.

## App changes backed by this capture

- The main monitor says **5G advertised · LTE only** when NR availability is
  advertised but EN-DC availability is explicitly false and no NR connection is
  established. Its visible summary explains that data remains on LTE.
- An explicit restricted NR state takes precedence over availability flags.
- Field reports include hardware SKU, because newer capability profiles differ
  by SKU even within the same Pixel generation.
- The Liquid monitor continues to show current physical CA components and their
  individual widths rather than a fixed `20 + 15 + 20` example.

These improve monitoring and diagnosis. They do not enable an NR secondary cell.

## Remaining acceptance gate

Identify a supported, firmware-matched configuration change from the captured
modem selection/policy evidence before changing carrier profiles, NV or UE files.
Preserve the stock baseline and a rollback path. Then verify actual NR attachment
on-device: `NR_STATE_CONNECTED` or matching current serving NR/physical/RRC
evidence during mobile data activity. For NSA, record the LTE anchor and n78
secondary in the same observation window. Check IMS/calls and LTE fallback too.

An active module, a 5G icon, an advertised capability, or a successful APK build
does not satisfy this gate. Publication of **2.2.7 Liquid** remains pending.

## Update 2026-11-03: offline analysis of the same capture

Released in 2.2.7 Liquid as a documented known limitation, not a fix.

- **The regional patch does not remove NR.** Applying every configuration module in
  order, the stock `wildcard` profile and the patched `it_iliad` profile both keep
  EN-DC mode enabled with an empty NSA block list. The patched profile additionally
  sets NR configuration mode on. Removed band-indicator entries are mmWave only.
- **UE capability is advertised.** The modem's own capability logs show EN-DC
  combinations sent to the network, including B3 + n78, B1 + n78, B8 + n78,
  B40 + n78 and B41 + n78, generated from the full platform capability profile.
  The generic stub capability file is not what the modem used.
- **Sri Lankan PLMNs have no explicit entries** in the modem configuration or the
  capability PLMN mapping; they use generic defaults that include those combinations.
- **Remaining split:** the serving B3 cell advertises NR for the PLMN, the phone
  advertises n78 combinations, but no NR leg is added and the modem reports EN-DC
  unavailable. Either the network does not configure NR for this device, or the
  modem disables EN-DC internally. The vendor RIL logs EN-DC capability changes with
  a cause code, but those logs were not part of this capture.
- The newest capability log, written at the last boot before capture, contained no
  band combinations. Its trigger is unknown and should be checked.

Next evidence: compare the same SIM in a Pixel 7 Pro at the same site; reproduce on
the Pixel 10 Pro XL and read the vendor RIL logs; only with approval, A/B the stock
profile by removing the patch and rebooting.
