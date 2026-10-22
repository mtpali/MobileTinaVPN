# MobileTina / AliMTP migration to v2rayNG 2.3.13

Target: requested upstream **2.3.13**, published 2026-10-22 as a pre-release.
Upstream commit: `01b4630fdd8ba8cebd8aad74683584823942871c`.
Previous upstream: 2.3.12, `118cf5bc7d0b290947b0827f5ad03e73018d8e5d`.

The working edition starts at `18c432189408a373ed29031a4a2b5f5665ed446f`;
the personal edition starts at `0339fbe779884877d38651ed4c2578413726db55`.
The two editions remain independent branches. Existing migration PRs #9 and #10
retain their working/personal bases. Main and the base branches are not merge targets
of this update without fresh authorization for their exact heads.

## Target build inputs

| Input | Exact target |
| --- | --- |
| versionName / source versionCode | 2.3.13 / 753 (previously 2.3.12 / 752) |
| Playstore ARMv7+ARM64 versionCode | 4000753 |
| AndroidLibXrayLite | `76a6e2172210613fcb4aa32b6de55bcd9452ce43` |
| Xray-core | v26.10.21, `1b1647cd9ddf0abc34b981d4611fc03bcac1ca74` |
| Xray Go module version | `v1.260327.1-0.20261021115930-1b1647cd9ddf` |
| HEV | 2.19.1, `107a5083cb33d729a476c8282745dfc2f12e48e2` |
| Go for native CI | 1.27.3 |
| Gradle wrapper | 9.8.2 |
| Java / Android / NDK | JDK 21; min 24, compile/target 37; NDK 30.0.16248370 |
| Native output | ARMv7 + ARM64; no x86 payloads |

Xray v26.10.21 is a security release updating its root certificate dependency and
requiring the Go 1.27.3 certificate fixes. Native CI pins Go 1.27.3 and the build
script rejects older toolchains. Native cache keys include the exact wrapper commit,
Go/NDK/API/ABI identity, both patches, recovery source, wrapper dependencies and build
script; HEV caches include its exact commit and NDK. An old AAR cannot satisfy this key.
The final APK gate also checks Go and Xray module markers in both libgojni payloads.

## Changes transferred from upstream

- Updated both native gitlinks, Gradle, application version and Java setup action.
- Transferred subscription update refresh signalling to the Compose subscriptions
  screen and the retained product subscriptions screen.
- Added upstream persisted `SubscriptionUsageInfo`, its parser, response-header callback
  and all upstream parser/UI-model tests. Custom request headers and User-Agent persist.
- Transferred upstream Compose group pills, count badges, subscription information card,
  group refresh and validated link handling. The manifest still launches the customized
  View-based Auto/Manual screen; the generic upstream launcher does not replace it.
- Connected metadata to the product's existing subscription optimizer and usage card.
  Both the fast path and full updater parse title, traffic, expiry, support and page
  headers. Successful header removal clears stale header metadata; failed/empty fetches
  retain the last usable snapshot. JSON `_comment` expiry and protected brand titles
  remain independent. Metadata changes do not force profile GUID regeneration.
- The legacy group DTO also carries the new usage info. No public subscription-URL
  shortcut is added to the product shell; protected QR/copy gestures retain their rules.
- Additional locale directories from upstream remain excluded by product policy;
  original default `en` resources and existing Persian product messages are retained.

## Native compatibility

Both existing AWG patches apply to the exact v26.10.21 source with `--fuzz=0` and
without rejected hunks. The locked `up()`/Close/recovery lifecycle contract was checked
against the patched source. No patch functionality was dropped to obtain application.
Remote DNS remains field 10 and AWG field 11. The infra/conf test filter now includes
all `TestAmnezia` cases, including the remoteDNS/AWG protobuf round-trip regression,
in addition to WireGuard/recovery and MASQUE package tests during native rebuild.

## Preserved product contracts

The full feature contracts in `MOBILETINA_2.3.12_MIGRATION.md` and the October 20
handoff remain applicable. In particular:

- Distinct package IDs, protected vault records/namespaces, immutable 2.2.6 record-key
  context, integrity checks and byte-identical artwork per edition.
- Working 226dp and personal 238dp Auto buttons; personal VPN widget only in its
  Playstore overlay; personal Persian messages and Telegram branding.
- Auto/Manual, hidden subscriptions, ten-second Drawer reveal and ten-click Manual QR.
- Silent tests, Smart Connect acknowledgement/cancel generations and eight-worker cap.
- Standard WireGuard/AWG import, optional Reserved policy, runtime keepalive and sleep/
  network recovery; real TUN close before stop acknowledgement and native cleanup.
- Expiry, retirement, usage optimization, boot recovery and 24-hour session limiter.
- Light/Dark/System across Views and Compose, OLED dark surfaces and blue accents.
- Removed settings, dynamic color and language selection stay removed.
- Runtime .ir bypass, FakeDNS/routeOnly behavior, full custom JSON preservation and
  GeoData checksums; latency probe configs remain excluded from bypass.

## Validation and acceptance

Local source audits, original artwork checks, personal UI checks, XML/dependency
checks, patch application and the patched WireGuard lifecycle contract are the local
gates. The editing environment's Java runs after setting its library search path,
but Gradle 9.8.2 cannot be downloaded because network access to its distribution
endpoint is unavailable. Android/native compilation and unit tests run in
`.github/workflows/mobiletina-2313-migration.yml` on GitHub Actions.

CI results, exact head/run and output checksums must be recorded after the new runs
complete; a successful old 2.3.12 run does not validate this target. Each distribution
includes upstream parser/UI-model tests and additional legacy metadata compatibility,
header removal, unlimited-plan and custom expiry preservation regressions.

Physical-device acceptance remains pending for the exact 2.3.13 APKs: launch, old data,
theme, real AWG traffic, sleep/network recovery, stop/reconnect, hidden gestures,
subscription refresh/expiry, .ir routing and the personal widget. Existing ECH/encrypted
DNS/IP-literal/Lua limitations remain. The recorded 2.3.12 device results are not new
2.3.13 acceptance.

Permanent keystores are not supplied by this migration. The workflow records whether
repository secrets or ephemeral CI test signing were actually used. A new temporary
certificate does not permit an in-place update over a different certificate. APK,
certificate digest and the exact R8 mapping must be archived together per edition.

## AliMTP VPN widget: background Smart Connect (2026-10-22)

The personal widget uses an immutable broadcast PendingIntent. Authorized connect,
cancel and disconnect taps do not launch MainActivity or any other activity. A missing
VPN grant opens only a non-exported transparent consent bridge and Android's VPN
permission dialog. Denial/cancellation does not start a connection; a second widget
tap while the dialog is open cancels its request. The bridge is excluded from Recents.

Both the Auto button and widget dispatch Smart Connect to the existing core service
for the selected VPN/proxy/root mode. That service enters foreground immediately using
the existing VPN notification and owns the selection coroutine independently of the
activity. There is no extra test notification, new test foreground service, overlay
permission, accessibility dependency, alarm workaround or background autostart.
CoreTestService remains a regular service with the existing eight-worker limit.

The shared SmartConnectRunner preserves immediate single-server start, two Real Ping
rounds (6 seconds, retry after 300ms, then 5 seconds), positive-result filtering,
350ms settle and the 15-second confirmed-core watchdog after selection. Probe IPC
checks both a fresh request UUID and batch acknowledgement before reading results;
unacknowledged stale positive pings cannot select a server. Auto retains its 1200ms
subscription-refresh grace and permission flow; a 33-second UI fallback additionally
bounds a daemon launch that never reports status. The normal selected-profile,
integrity, root-availability and runtime-SOCKS-port validation is shared with manual
starts. The current subscription is loaded from MMKV without constructing a UI model.

After selection, a request-tagged regular start command goes to the SAME foreground
service, preserving normal sticky VPN behavior without a second foreground-service
launch after the widget interaction allowance expires. Cancelled or replaced handoff
tokens are rejected. Teardown cancels the selection job and probes before existing
TUN-first/native cleanup. Stop from the widget, Auto, the notification or explicit
service shutdown invalidates pending work. Cross-process state updates keep the
widget's stop icon and the Auto screen synchronized; confirmed running state remains
separate and stale pending state is reconciled against own core services.

Original artwork, widget dimensions, 238dp Auto button, native inputs, fixed vault
context, session/expiry policies and hardened signing checks are preserved. This
change is AliMTP-only; MobileTina stays at
`4dcaab4b49986341aeea65e3f380297954e43130`.

Focused tests cover selection, failed/absent acknowledgements, retry identity,
fast settle, cancellation, no usable profiles, confirmed startup timeout, immutable
broadcast dispatch, activity-free start/stop and the consent-only exception. Source
checks require foreground promotion before command dispatch and teardown cancellation
in all three core modes. Both distribution unit suites and the hardened ARM build
must validate the new exact source head. Physical-device acceptance remains pending
for cold/warm launcher taps, app removal during selection, rapid cancel, consent
allow/deny/cancel, notification stop, failure, package upgrade and real traffic.
