# 2.3.14 final without per-app usage history

Per the current user request, remove the per-app usage feature from both editions: settings entry, activity, DTO/store/reader/manager, reset/core hooks, JNI source map, Xray accounting wrapper, HEV instrumentation, native patches and obsolete feature tests/documents. Existing diagnostic VPN logging and notification aggregate speed/traffic behavior are retained.

The pre-feature, per-edition CoreServiceManager/Settings/Reset/TProxy sources and native build contracts are restored. No per-app polling, stored totals or buffered accounting path is built or started. Original upstream Xray process lookup remains for its own routing features. Native cache keys bind the restored inputs, and scripts/verify-no-usage-feature.py rejects stale usage collectors in source and the actual APK DEX/ARM libraries.

MobileTina only: launch notice retains its two original Instagram links and pink styling; the third Telegram notice record is removed. About button three restores exactly the previous encrypted Instagram mobile.tinaa URL and label. About contact four remains Telegram mobile_tina. Other vault records, artwork, adaptive insets, current notification button, quota fixes, dock ping, import/reset fences, per-app defaults, 1.5-second subscription hold, protected Raw/# behavior, AWG/recovery and plain VLESS compatibility are preserved.

The MobileTina runtime text digest and notice digest are updated together with the vault tests and production guard regression. Package/artwork/signer checks are not bypassed. AliMTP retains its existing integrity policy and unchanged social/artwork sources.

Removal does not read or migrate old usage totals and does not clear unrelated app data. An in-place upgrade may leave the retired MMKV files on disk, unused. No usage UI, worker, timer or native accounting code can access them in this build. Data clearing or uninstall removes those files.

Run source absence checks, Playstore/F-Droid unit tests and debug builds, hardened ARMv7/ARM64 release, signature/obfuscation/artwork checks, VLESS before/after evidence and packaged native provenance. No phone or battery benchmark is implied by CI success. Permanent signing credentials compatible with installed releases are still required for upgrade-compatible distribution.
