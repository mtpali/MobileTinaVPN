# MobileTina / AliMTP migration to v2rayNG 2.3.14

The requested upstream release is **2.3.14**, published on 2026-10-28 as a
pre-release. Its exact tag commit is
`aa4b05503a504a3f2e43818e6ee8ebf1caaa3df0`; the previous transferred upstream is
2.3.13, `01b4630fdd8ba8cebd8aad74683584823942871c`.

The working edition starts at `1afeae6fb0f31371b697ceae1e5b16c8118cb066` and the
personal edition at `683d49ce549a874aad0ccc99289492cdda2cd59c`. Existing migration
PRs #9 and #10 keep their independent branches and bases. This migration does not
merge those PRs or replace the older main/base branches.

## Exact inputs

| Input | Target |
| --- | --- |
| versionName / source versionCode | 2.3.14 / 754 |
| Playstore ARMv7 + ARM64 versionCode | 4000754 |
| AndroidLibXrayLite, unchanged | `76a6e2172210613fcb4aa32b6de55bcd9452ce43` |
| Xray-core, unchanged | 26.10.21, `1b1647cd9ddf0abc34b981d4611fc03bcac1ca74` |
| HEV | 2.19.3, `c5584feca8f6df70ee53eb2879e034b849a3d15a` |
| OkHttp | 5.5.1 |
| Go / Gradle / JDK | 1.27.3 / 9.8.2 / 21 |
| Android min / compile / target | 24 / 37 / 37 |
| NDK | 30.0.16248370 |
| Release native payload | armeabi-v7a + arm64-v8a |

## Upstream changes transferred

The complete 2.3.13-to-2.3.14 comparison contains six commits and eight paths.
Seven paths are transferred; only the Azerbaijani translation update is excluded
under the existing product locale policy. No new locale directory is restored.

- HEV is upgraded from 2.19.1 to 2.19.3. Both the in-process JNI tunnel and the
  standalone Root tunnel executable are built from the new exact gitlink.
- Root LAN configuration disables jumbo offload; IPv6 LAN forwarding also disables
  TSO. Local Root without LAN sharing retains the default offloads. The configured
  MTU, IPv4/IPv6 selection and quoted SOCKS credentials remain intact. All three
  new upstream Root configuration tests are included.
- `hysteria2://` and `hy2://` links without an explicit port now use 443. Explicit
  ports and TLS fields retain their previous meaning. All eight new upstream
  parser tests are included, including IPv6 and URI export/import round trips.
- OkHttp is upgraded from 5.5.0 to 5.5.1; application version metadata is 754/2.3.14.

## Product behavior retained

The current comprehensive handoff is authoritative for the detailed contracts.
Historical migration documents describe their own snapshots; obsolete gestures
in an older document must not be restored during a future migration.

- Distinct package IDs `com.v2ray.mobiletina` / `com.v2ray.alimtp`, independent
  artwork and personal-only daemon-owned Smart Connect/widget behavior remain.
- All artwork bytes stay unchanged from each edition's starting head. MobileTina
  adaptive foreground inset remains 16%; AliMTP remains 10%. MobileTina's updated
  status images do not propagate to AliMTP. Auto image sizes remain 226dp/238dp.
- Ordinary server actions retain Persian edit/delete labels and QR/copy sharing.
  Protected Raw GitHub or `#` subscriptions retain sticky persisted restrictions,
  hidden action controls and execution-time checks. Header QR uses a deliberate
  two-second hold. Drawer/nav hold-to-reveal access stays removed.
- Notification stop controls stay white, 140x32dp, with black bold 11sp text and a
  148x48dp touch area in compact and expanded layouts. Light-mode MobileTina About
  surfaces stay white. Notification availability still follows Android permission
  and system/OEM policy.
- Scanner one-shot result dispatch, close-before-callback and bounded decoding,
  import generation fences, asynchronous import, reset cancellation/locking and
  confirmed VPN teardown are retained. Android 16 phone acceptance is separate
  from unit/CI results.
- Per-app icon IO concurrency, density/version/theme-aware memory caching and
  first-use installed-app defaults are retained. Telegram, Instagram, WhatsApp,
  Chrome and Google Search are initially checked if installed; per-app enable
  and bypass switches retain their disabled defaults and saved choices win.
- AWG import/export, optional Reserved handling, remoteDNS field 10 / AWG field
  11, sleep/network recovery and runtime keepalive remain intact. The narrow
  plain-VLESS compatibility patch and real local TCP round-trip regression remain;
  TLS/REALITY/encryption validation and the Trojan TLS restriction are preserved.
- Subscription usage/title/support/page metadata, fast-path refresh, expiry,
  retirement, 24-hour session limiter, `.ir` runtime routing, full custom JSON,
  GeoData payloads, vault records and immutable 2.2.6 key context remain intact.
- Integrity checks, R8/resource hardening, native descriptor keeps, Light/Dark/
  System behavior and existing removed settings remain intact.

## Build provenance and validation

The current workflow is `.github/workflows/mobiletina-2314-migration.yml`.
Source boundaries and artwork checks run before compilation. The workflow runs
Playstore and F-Droid debug unit suites, builds both debug distributions and a
hardened ARM release, then verifies version/package, signatures, ABIs, GeoData,
native compatibility evidence and protected DEX/resource contracts.

The historical `mt2313-*` cache prefixes are cache namespaces, not application
versions. The exact HEV commit is part of its key, so 2.19.1 cannot satisfy the
new HEV key. Xray inputs are unchanged; its AAR cache can be reused only with the
matching source-input hashes and native before/after proof. Both ARM ELF runtime
profiles in the packaged APK must match the tested AAR despite Android stripping.

Archive the actual successful run/head, JUnit XML, native JSONL/proof, packaged
native proof, APK hash, public signing certificate and R8 mapping for each edition.
The updated comprehensive handoff and validation bundle hold these final values;
successful older 2.3.13 artifacts do not validate a 2.3.14 build.

Physical-device acceptance remains necessary for real Root/LAN/IPv6 traffic,
Android 16 scanning/reset, AWG sleep recovery, live VLESS endpoints and the personal
widget. Local TCP core tests do not prove remote endpoint reachability.

The workflow uses repository signing secrets if available, otherwise a temporary
CI certificate. Report the actual signing source and fingerprints. A different
temporary signer cannot update an installed APK in place; retain the original
private signing key for production upgrades. Do not put a private key or real
password in this report or the comprehensive handoff.
