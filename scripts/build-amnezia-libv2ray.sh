#!/usr/bin/env bash

set -euo pipefail

if [[ $# -ne 1 ]]; then
    echo "Usage: $0 <output-aar>" >&2
    exit 2
fi

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
android_lib="$repo_root/AndroidLibXrayLite"
core_patch="$repo_root/core/patches/xray-core-amneziawg.patch"
output_aar="$(realpath -m "$1")"
core_module="github.com/xtls/xray-core"
core_version="$(awk '$1 == "github.com/xtls/xray-core" { print $2; exit }' "$android_lib/go.mod")"
[[ -n "$core_version" ]]
task_dir="$(mktemp -d)"
patched_core="$task_dir/xray-core"
wrapper="$task_dir/AndroidLibXrayLite"
native_reports="$repo_root/build/native-tests"

cleanup() {
    chmod -R u+w "$task_dir" 2>/dev/null || true
    rm -rf -- "$task_dir"
}
trap cleanup EXIT

command -v go >/dev/null
command -v gomobile >/dev/null
command -v patch >/dev/null
build_go_version="$(go env GOVERSION)"
python3 - "$build_go_version" <<'PY'
import re
import sys
version = re.fullmatch(r'go(\d+)\.(\d+)\.(\d+)', sys.argv[1])
if version is None or tuple(map(int, version.groups())) < (1, 27, 3):
    raise SystemExit('Xray v26.10.21 security build requires Go 1.27.3 or newer')
PY
[[ -d "$android_lib" ]]
[[ -s "$core_patch" ]]

module_json="$(GOWORK=off go mod download -json "$core_module@$core_version")"
core_source="$(python3 -c 'import json,sys; print(json.load(sys.stdin)["Dir"])' <<<"$module_json")"
[[ -d "$core_source" ]]

mkdir -p "$patched_core" "$wrapper" "$(dirname "$output_aar")" "$native_reports"
cp -a "$core_source/." "$patched_core/"
cp -a "$android_lib/." "$wrapper/"
chmod -R u+w "$patched_core" "$wrapper"

patch --batch --forward --fuzz=0 --silent -p1 -d "$patched_core" < "$core_patch"
patch --batch --forward --fuzz=0 --silent -p1 -d "$patched_core" < "$repo_root/core/patches/xray-core-amneziawg-recovery.patch"
patch --batch --forward --fuzz=0 --silent -p1 -d "$patched_core" < "$repo_root/core/patches/xray-core-app-traffic.patch"
cp "$repo_root/core/mobile-recovery/libv2ray_recovery.go" "$wrapper/"
cp "$repo_root"/core/app-traffic/wrapper/*.go "$wrapper/"
cp "$repo_root"/core/app-traffic/dispatcher/*.go "$patched_core/app/dispatcher/"
mkdir -p "$patched_core/testing/mobiletina_compat"
cp "$repo_root"/core/vless-compat/*.go "$patched_core/testing/mobiletina_compat/"

# Xray v26.10.21 creates the device eagerly and raises it through locked up().
# Keep setup, recovery registration and shutdown in that same lifecycle lock.
python3 - "$patched_core/proxy/wireguard/client.go" <<'PY'
from pathlib import Path
import sys

source = Path(sys.argv[1]).read_text()
process_start = source.index("func (h *Handler) Process(")
process_end = source.index("func (h *Handler) Close()", process_start)
process = source[process_start:process_end]
up_start = source.index("func (h *Handler) up(")
up_end = source.index("func MakeResolveLocal(", up_start)
up = source[up_start:up_end]
close = source[process_end:up_start]

if 'if err := h.up(ctx); err != nil {' not in process:
    raise SystemExit("WireGuard Process must initialize and raise the device through the locked helper")
if 'if h.dev == nil {' in process or 'h.dev.Up()' in process:
    raise SystemExit("WireGuard Device.Up must not run outside Handler.mu")
for required in ('h.mu.Lock()', 'defer h.mu.Unlock()', 'if h.dev == nil {', 'return errors.New("closed")', 'h.dev.Up()', 'amneziaHandlers.Store(h, struct{}{})'):
    if required not in up:
        raise SystemExit(f"missing locked WireGuard lifecycle contract: {required}")
if up.index('h.mu.Lock()') > up.index('h.dev.Up()'):
    raise SystemExit("WireGuard device raised outside its lifecycle lock")
if 'amneziaHandlers.Delete(h)' not in close:
    raise SystemExit("Closed Amnezia handlers must leave the recovery registry")
PY

(
    cd "$patched_core"
    gofmt -w infra/conf/wireguard.go infra/conf/wireguard_amnezia_test.go proxy/wireguard/*.go testing/mobiletina_compat/*.go app/dispatcher/mobile_app_traffic*.go app/dispatcher/default.go
    GOWORK=off go mod tidy
    # First reproduce the user's failure on the pinned upstream core. A compile
    # failure is not accepted as reproduction; the verifier checks the exact error.
    if GOWORK=off go test -json ./testing/mobiletina_compat \
        -run '^TestMobileTinaVlessCompatibility$/^empty-security$' -count=1 -timeout=60s \
        > "$native_reports/before.jsonl"; then
        echo "Expected the upstream plaintext VLESS config to be rejected" >&2
        exit 1
    fi
    python3 "$repo_root/scripts/verify-vless-compat.py" baseline
    patch --batch --forward --fuzz=0 --silent -p1 < "$repo_root/core/patches/xray-core-vless-compat.patch"
    gofmt -w infra/conf/xray.go
    GOWORK=off go test -json ./testing/mobiletina_compat -count=1 -timeout=90s \
        | tee "$native_reports/after.jsonl"
    GOWORK=off go test ./infra/conf -run 'TestAmnezia' -count=1
    GOWORK=off go test ./proxy/wireguard ./proxy/masque -count=1
    GOWORK=off go test -race -json ./app/dispatcher -run '^TestAppTraffic' -count=1 -timeout=90s \
        | tee "$native_reports/app-traffic.jsonl"
)

(
    cd "$wrapper"
    GOWORK=off go mod edit "-replace=github.com/xtls/xray-core=$patched_core"
    GOWORK=off go mod tidy
    gofmt -w libv2ray_app_traffic.go
    GOWORK=off gomobile bind \
        -v \
        -target=android/arm,android/arm64 \
        -androidapi 24 \
        -trimpath \
        "-ldflags=-s -w -buildid= -checklinkname=0" \
        -o "$output_aar" \
        ./
)

[[ -s "$output_aar" ]]
python3 "$repo_root/scripts/verify-vless-compat.py" record "$output_aar"
