# App traffic log and 1.5-second subscription QR — 2026-10-28

Both editions continue from the successful UI-fix heads:
MobileTina `c204ce15b8ccd86e858f2c6e47e02a9ce3449a1d` and
AliMTP `e5b0b14d999175744190cf4b378a0cdd10f2f91c`.
The UI-only CI runs are 40646541563 and 40646559914; both succeeded.

## Minimal log screen

An always-visible **log** button is last in Settings. The non-exported
`ui/traffic/AppTrafficLogActivity` shows only app-name/total-usage rows, highest
usage first, and a trash icon in its toolbar. No icons, packet lists, destinations,
domains, URLs, charts or explanatory paragraphs are loaded. If needed, the single
**دسترسی مصرف** button opens Android's Usage Access settings. Access is never
silently granted; without it Android does not expose other applications' counters.

Logging is enabled by default and independent of the speed-notification switch.
After an actual VPN/TUN core start, the existing daemon starts one coroutine.
It samples Android NetworkStatsManager summaries for Wi-Fi and mobile about once
per minute on IO, not once per application or packet. Ethernet is queried only
when an Ethernet transport is active. Opening log can request a coalesced refresh;
network queries are limited to at most one every 15 seconds. While the page is
visible, its five-second refresh reads only the small local aggregate. Nothing
starts a new foreground service, periodic WorkManager task or wake lock.

Only successful VPN/TUN starts open a logging window. Smart Connect probes,
consent prompts, the disconnected interval, Root and proxy-only modes do not open
one. Per-app allow/bypass membership is captured from the actual VPN settings;
disabled per-app mode or an empty set retains the existing all-apps behavior.
The VPN application's own UID is omitted to avoid counting its tunnel overhead
as another application. The data source is Android's per-UID network accounting
within the connected interval, not decrypted VPN payload accounting: app traffic
routed directly inside that interval can also contribute. System accounting can
be delayed/coarse and is not a live packet meter or a subscription-panel quota.

## Persistent state and clearing

`dto/entities/AppTrafficLogState.kt` contains the Gson DTOs and pure accumulation
policy. Existing DTO keep rules preserve their persisted field names/constructors
while allowing class renaming. `handler/AppTrafficLogStore.kt` uses a separate
`APP_TRAFFIC_LOG_V1` MMKV file in MULTI_PROCESS_MODE with a process monitor and
MMKV's cross-process lock. Labels, package identity and aggregate bytes persist
across reconnects/restarts. Rows sharing an Android UID are one named group;
they cannot be split reliably with this API. UID reassignment on a later session
has a different package-identity key and does not rename old history.

Every session has its own start and cumulative high-water counters. Repeated,
temporarily smaller or incomplete snapshots do not double-count earlier usage.
Names are resolved on IO only for UIDs with positive usage and cached per session.
No installed-app icon list is loaded. Counters saturate instead of overflowing.

Trash increments a generation, erases totals, and starts the active session's
accounting window again at the clear time. In-flight samples from the previous
generation are rejected. The VPN stays connected and later consumption is logged.
The full Drawer reset calls `clearHistory(endSessions=true)` after confirmed stop;
all active/pending session identities are removed, so late final samples cannot
recreate erased history. Log storage is independent of the profile index lock.

Stop cancels the sampling loop immediately and unregisters the refresh receiver.
A final IO query is bounded to the captured stop time, with a 2.5-second accounting
settling delay. VPN/TUN close and stop acknowledgement never await that query.
Quick reconnects retain the previous pending final session without charging the
disconnected interval. After process death, unowned stale sessions are discarded;
the most recent uncommitted sample/tail may be lost, while committed aggregates
survive. Queries/storage failures do not stop the VPN or erase a valid snapshot.

The refresh broadcast is package-scoped and its receiver NOT_EXPORTED. Data
stays in the app's private storage; no export/upload or browsing-history access
is added. The two package IDs retain independent histories. NetworkStats API
permission and Android/OEM accounting limitations must remain explicit in future
handoffs; do not substitute unsupported other-UID TrafficStats calls or invented
per-app allocations from the VPN's global counter.

## QR timing

`SubscriptionHeaderHold` now posts the action after **1_500ms** rather than 2_000ms.
The CI timing contract is updated. Slop, multiple pointers, pause/detach/destroy,
tab/ownership checks and protected Raw/# subscription policy remain unchanged.

## Validation

Nine pure state regression tests cover cumulative deltas, repeated/lower snapshots,
reconnects, trash/reset generation fences, bounded pending final samples, per-app
membership, shared/reassigned UIDs, Gson persistence and counter overflow. Both
distribution suites and hardened releases run in the existing 2.3.14 workflow.
Exact run/head/APK/signature/mapping and final validation results belong in the
updated comprehensive handoff. Real Android permission UI, counter reporting and
device visuals require device acceptance; no physical-device result is invented.

Official API reference:
https://developer.android.com/reference/android/app/usage/NetworkStatsManager
