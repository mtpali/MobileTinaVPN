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
