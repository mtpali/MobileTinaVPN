# VPN-only per-app traffic correction — 2026-10-28

This file supersedes the V1 accounting described in MOBILETINA_2026_10_28_APP_TRAFFIC_LOG.md. It applies to both editions. MobileTina social changes below are exclusive to its branch.

## Measurement contract

- V1 queried Android NetworkStatsManager. Time buckets/underlying network usage cannot identify this VPN's proxy payload. Never restore querySummary, Usage Access or PACKAGE_USAGE_STATS for log.
- V2 counts upload/download payload inside Xray's routed dispatcher, after outbound selection. Freedom/direct (including .ir bypass), blackhole, DNS and loopback handlers are excluded by protocol type, regardless of custom outbound tag.
- Only actual VPN/TUN starts attach a recorder. Probes, proxy-only and Root modes have none. Local SOCKS/HTTP clients and LAN clients must not masquerade as VPN apps.
- The original TUN source and destination go through Android getConnectionOwnerUid (API29+). Own UID and unknown owners are rejected. No whole-device fallback or guessed attribution is permitted.
- HEV mode preserves original source tuples in a short-lived native map indexed by its loopback proxy protocol/port. Register before SOCKS handshake/first UDP payload; remove on session destruction. JNI copies only a source IP/port per flow. This is memory metadata, not saved endpoint/packet history. Other TUN mode uses its original source directly.
- Payload counters run in Go, with atomic saturated 64-bit sums and a short recorder lifecycle lock. No Kotlin/JNI callback per packet, payload scan, extra packet bridge/copy, service, worker, wakelock or telemetry upload is added.
- Raw socket splice bypasses readers/writers and reports at EOF. For attributed flows the existing buffered forwarding path is retained so long-running transfers produce live counts. This has some CPU cost compared with raw splice; do not claim zero overhead.
- Values are app data submitted/read through the selected proxy, not ISP/subscription quota or encrypted tunnel wire bytes. Protocol overhead/retransmissions/DNS may differ. Android shared UIDs form one group; missing ownership fails closed. Custom nested loopback detours are not guessed. API24–28 retain VPN functionality but show one concise unsupported message in log; API29+ needs no Usage Access grant.

## History and lifecycle

- Default enabled, independent of speed notifications. Only aggregated app labels/package identity/UID/bytes are saved in APP_TRAFFIC_LOG_V2 (multi-process MMKV). Retire and clear V1 whole-device history; it cannot be converted accurately.
- Cumulative snapshot every 60 seconds, or a lightweight refresh while the log page is STARTED (5 seconds). Resolve/cache names on IO once per used UID/session. Sort descending bytes; retain the small name:volume list and toolbar trash.
- Stop freezes the current handle immediately; label/disk work is queued on IO, without delaying TUN teardown acknowledgement. No old 2.5-second system accounting wait remains. Reload closes/persists the old handle and creates an independent new one. In-flight old snapshots cannot read a newer core.
- Trash increments persisted generation, rejects old windows, and requests an atomic native reset. Existing connected downloads continue with zeroed counter objects. A second clear cannot be acknowledged by an older reset. Bytes between clicking clear and its daemon acknowledgement are discarded rather than reintroduced from old history.
- Drawer full reset clears both current/pending sessions and history after stop. Late final samples cannot resurrect it. Committed totals survive process death; the unsaved tail since the last snapshot can be lost on forced process death. No wake lock/fsync per packet is justified.
- Per-app Enable/Bypass and their defaults stay unchanged. Actual TUN capture is the authoritative filter; user app-selection preferences are not written by logging.

## UI and contact changes

Both notifications: white stop button 128x32dp, black bold12sp, centered RTL text, 136x48dp clickable area. The previous values were 140dp/11sp/148dp. Ongoing behavior/PendingIntent and aligned horizontal rows remain.

MobileTina only: first-launch line uses exactly the close button's pink RGB(193,53,132). Third social label is `Telegram : mobile_tina`; its URL is `https://t.me/mobile_tina`. About (X7) developer label is `ارتباط در تلگرام`, same URL. The third About social label is also Telegram because it shares that URL record. Telegram opens through tg://resolve with an HTTPS fallback if no handler is installed. First/second Instagram records remain unchanged. Update only the five affected AES-GCM q records (2,3,6,7,11), matching first-launch digest and vault tests. Hidden r/w vault contexts, protection rules, brand artwork and AliMTP social records remain unchanged.

## Rebuild and validation

- Exact upstream gitlinks remain AndroidLibXrayLite76a6e217 and HEVc5584feca; changes are explicit parent-repo patches/additions, not dirty submodules.
- Build script applies Xray dispatcher patch, copies app-traffic Go implementation/wrapper/tests, and runs six focused Go tests with race detector. Existing VLESS reproduction/round-trip and AWG tests remain mandatory.
- HEV build works on a temporary source copy, applies its source-map/JNI patch, and explicitly passes the upstream REV_ID to both native targets. Include patch, C/header/tests and verifier in cache keys.
- C test covers IPv4/IPv6 original sources, separate TCP/UDP keys, missing/reused tuples, old destructor safety, cleanup and eight concurrent workers under address/undefined sanitizers. Local container LeakSanitizer cannot inspect process tasks, so only its leak phase is disabled locally; CI uses the normal sanitizers. Registry nodes do not allocate heap memory.
- Bind tested native AAR and HEV outputs to source hashes. Compare all loader-visible ELF sections/program headers against final stripped ARM APKs; preserve JNI/gomobile descriptors including libv2ray.AppTrafficLog and TProxyGetSource.
- Android unit tests cover repeated/reconnected snapshots, clear/reset races, shared/reassigned UIDs, persisted Gson, missing package identities, parser validation, overflow and clock changes. MobileTina contact vault tests assert exact new labels/URLs; AliMTP original vault tests remain intact.
- Device acceptance still required: Android16 actual Telegram/Instagram TCP+QUIC downloads, VPN-off/direct/bypass exclusion, stop/reconnect/trash mid-download, process death and both TUN choices; compact/expanded notification at large font; actual Telegram deep link and first-launch appearance. No emulator/phone or real public VPN endpoint test is claimed.
