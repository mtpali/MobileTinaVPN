# MobileTina / AliMTP upstream migration

Target: v2rayNG **2.3.11**, upstream commit `e1b73f2fa8487dfc303da52d109719c1e32386b4`.
Native wrapper: `1f3e2c5df1c248ed42630cee2bf747643d303ecc`.
Xray: **v26.10.10**, commit `701af60772cda123492f86e383e1fdba066614a9`.

The existing MobileTina main screen and hidden subscription screen intentionally retain their
View-based implementations and Material themes. Editors, settings, routing, assets, scanner and
per-app proxy use the new upstream Compose implementations. This is a compatibility migration;
it does not replace the custom Auto/Manual product interface with the upstream launcher.

Both application IDs remain unchanged. Source versionCode advances from 744 to 751 and
versionName from 2.2.6 to 2.3.11. Shop and personal editions remain separate branches.

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
Upstream's locked WireGuard initialization is retained rather than reapplying the old race patch.

The GitHub migration workflow runs the complete Playstore unit suite, Kotlin compilation,
Playstore and F-Droid debug assembly, hardened release assembly, signature/resource/ABI checks,
and native AWG tests. JUnit Vintage runs the preceding release's JUnit 4 regressions alongside
the upstream Jupiter suite.

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
