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
core_module="github.com/autorepobot/xray-core"
core_version="v0.0.0-20260704054728-50c452881eb9"
task_dir="$(mktemp -d)"
patched_core="$task_dir/xray-core"
wrapper="$task_dir/AndroidLibXrayLite"

cleanup() {
    chmod -R u+w "$task_dir" 2>/dev/null || true
    rm -rf -- "$task_dir"
}
trap cleanup EXIT

command -v go >/dev/null
command -v gomobile >/dev/null
command -v patch >/dev/null
[[ -d "$android_lib" ]]
[[ -s "$core_patch" ]]

module_json="$(GOWORK=off go mod download -json "$core_module@$core_version")"
core_source="$(python3 -c 'import json,sys; print(json.load(sys.stdin)["Dir"])' <<<"$module_json")"
[[ -d "$core_source" ]]

mkdir -p "$patched_core" "$wrapper" "$(dirname "$output_aar")"
cp -a "$core_source/." "$patched_core/"
cp -a "$android_lib/." "$wrapper/"
chmod -R u+w "$patched_core" "$wrapper"

patch --batch --forward --silent -p1 -d "$patched_core" < "$core_patch"

# The pinned Xray snapshot predates upstream's WireGuard initialization-race
# fix (XTLS/Xray-core#6461). Keep Device.Up() under Handler.mu, matching the
# serialized device lifecycle used by Exclave's WireGuard client.
python3 - "$patched_core/proxy/wireguard/client.go" <<'PY'
from pathlib import Path
import sys

source = Path(sys.argv[1]).read_text()
process_start = source.index("func (h *Handler) Process(")
process_end = source.index("func (h *Handler) Close()", process_start)
process = source[process_start:process_end]
init_start = source.index("func (h *Handler) init(")
init_end = source.index("func (h *Handler) resolveLocal(", init_start)
init = source[init_start:init_end]

if 'if err := h.init(ctx); err != nil {' not in process:
    raise SystemExit("WireGuard Process must initialize and raise the device through the locked helper")
if 'if h.dev == nil {' in process or 'h.dev.Up()' in process:
    raise SystemExit("WireGuard Device.Up must not run outside Handler.mu")
for required in ('if h.tun == nil {', 'return errors.New("closed")', 'return h.dev.Up()'):
    if required not in init:
        raise SystemExit(f"missing locked WireGuard lifecycle contract: {required}")
PY

(
    cd "$patched_core"
    GOWORK=off go test ./infra/conf -run 'TestAmneziaWG' -count=1
    GOWORK=off go test ./proxy/wireguard -count=1
)

(
    cd "$wrapper"
    GOWORK=off go mod edit "-replace=github.com/xtls/xray-core=$patched_core"
    GOWORK=off go mod tidy
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
