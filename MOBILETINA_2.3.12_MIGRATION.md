# MobileTina / AliMTP upstream migration

Target (requested upstream pre-release): v2rayNG **2.3.12**, upstream commit `118cf5bc7d0b290947b0827f5ad03e73018d8e5d`.
Native wrapper: `1be1e621be15702da7b7faf16ef83f2493c5aa01`.
Xray: **v26.10.17**, commit `92ccc939aaf4f8965f369cf0b142e005aeaa11d5`.

The existing MobileTina main screen and hidden subscription screen intentionally retain their
View-based implementations and Material themes. Editors, settings, routing, assets, scanner and
per-app proxy use the new upstream Compose implementations. This is a compatibility migration;
it does not replace the custom Auto/Manual product interface with the upstream launcher.

Both application IDs remain unchanged. Source versionCode advances from 744 to 752 and
versionName from 2.2.6 to 2.3.12. Shop and personal editions remain separate branches.

| Capability | Migration contract |
| --- | --- |
| Auto/Manual, Smart Connect, progress and ping | Existing main screen and ViewModel retained; typed daemon results adapted; Smart Connect batch acknowledgement, cancellation generations and concurrency cap preserved. |
| Standard WireGuard and AmneziaWG | Raw INI/link parsing, address policy, optional Reserved, all AWG fields and backend retained alongside remoteDNS and finalMask. |
| Resume and network handover | AWG bind refresh retained; upstream network monitor integrated with stale-instance/session checks. |
| Stop and reconnect | TUN closes before publication and slow native cleanup; new startup waits for native cleanup on IO. |
| Force Stop / OEM Close All | Own-service snapshot reconciliation retained. |
| GeoIP custom JSON | Compact private/cn rules normalize before either TUN or proxy-only processing; full geo databases are bundled with pinned checksums. |
| Subscription privacy and sharing | Existing hidden drawer entry, read-only screen, ten-click action, allowed ten-second QR hold and protected strings remain. |
| Subscription expiry and usage | Comment markers, stop acknowledgement, retirement and usage metadata retained; HTTP request headers participate in fetch identity. |
| Branding and artwork | Existing images checked byte-for-byte; custom shell keeps its original theme. |
| Per-app proxy | Current Compose implementation retains the five pinned apps and persisted package selection. |
| Hardening | Certificate guard, R8, resource shrinking, protected strings and JNI descriptor checks retained. |
| Packaging | Release output contains ARMv7 and ARM64 only, including both HEV native components. |

The native patch was rebased onto the wrapper's exact Xray dependency. Upstream now owns protobuf
field 10 for remote DNS; Amnezia uses field 11. A protobuf round-trip test checks both together.
Xray's new eagerly-created WireGuard device and locked `up()` lifecycle are retained. AWG device options are applied before peers, and recovery registration is removed only when the handler closes. Patches carry context and apply with zero fuzz.

The GitHub migration workflow runs the complete Playstore unit suite, Kotlin compilation,
Playstore and F-Droid debug assembly, hardened release assembly, signature/resource/ABI checks,
and native AWG tests. JUnit Vintage runs the preceding release's JUnit 4 regressions alongside
the upstream Jupiter suite.

## Protected text compatibility

The previous migration APK could throw `AEADBadTagException` while normalizing subscription
names. Its brand record was encrypted with the original 2.2.6 key context, but decryption derived
a different key from the new release's `VERSION_NAME`. That immutable record context is now
independent of the APK version. GCM authentication and the integrity guard remain enabled.
The working edition also normalizes the F-Droid application ID suffix for its social/brand
records, matching its existing hidden-access vault. The personal edition keeps its shared
vault namespace and its distinct personal records.

Both Playstore and F-Droid unit suites now decrypt every social, hidden-access and brand record
and compare their contents with digests from the preceding working release. This checks exact
text preservation without putting protected text in fixtures or test output.

## Silent tests, settings and OLED (2026-10-20)

Real Ping and Smart Connect use a regular, user-initiated test service with in-app progress.
The dispatcher, service, manifest and notification channel were changed together so there is
no test notification and no foreground-service notification deadline. Batch acknowledgement,
request identity, generation cancellation and the eight-worker Smart Connect cap are retained.
The VPN's connection notification and subscription-update service retain their existing behavior.

The mode-help link, system-VPN settings shortcut and auto-connect-at-startup setting are removed
from the settings UI, handlers, resources and persistence API. Boot/package-update handling still
recovers pending subscription expiry work, but no longer starts a VPN. Android's separate
Always-on VPN support is retained. Sniffing and dynamic color default to off consistently in
the settings, core and theme readers; saved choices are respected.

Dark mode uses black backgrounds and surfaces in both Compose screens and the retained View
screens, including black window backgrounds and disabled elevation tint. Dynamic dark palettes
also receive the OLED surfaces when explicitly enabled. Foreground/accent colors are preserved.
The uploaded white.webp replaces only MobileTina's asset (SHA-256
`cd00d42eed21c4790e94ce7dbd62740304dbce31c25bc8cb2ac492bd4a95d3b3`),
with a 224dp square fitted image. Its release integrity digest and CI artwork digest were updated
together. AliMTP's artwork and image dimensions remain unchanged.

Regression coverage checks regular-service dispatch and cancellation while retaining the batch
request identity. The migration audit rejects reintroduced settings entries or test notifications
and checks that boot expiry recovery and disabled defaults remain present.

## Device acceptance

**Not run in the editing environment:** Gradle commands could not start because its Java runtime
cannot load `libjli.so`. GitHub Actions is the build/test authority for this change.

**Not run without a device/emulator:** VPN/proxy/root cold start and duplicate start; stop during
setup; repeated stop and immediate reconnect; sleep/wake and Wi-Fi/cellular handover racing stop;
OEM Close All/Force Stop; existing-data upgrade; first-run permissions; hidden subscription access,
QR/copy holds and expiry; IME, keyboard, D-pad, TalkBack and activity recreation in migrated screens.

A green build does not establish these runtime outcomes. Keep the migration PRs unmerged until
the exact artifacts and the product's device scenarios are accepted. Ephemeral CI certificates
remain test signing unless each edition is configured with its permanent keystore.
