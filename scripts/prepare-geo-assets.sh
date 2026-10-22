#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
asset_dir="$repo_root/V2rayNG/app/src/main/assets"
release=202610182205
mkdir -p "$asset_dir"

for asset in geoip.dat geosite.dat; do
    case "$asset" in
        geoip.dat) expected=010bfc2e353c5e34e23156004944444ca260bae999e73171bdd7974056557959 ;;
        geosite.dat) expected=3691de3874692fc8709686998c7627d030df3910d21f3d68232aaa0f1249dd2d ;;
    esac
    temporary="$(mktemp "$asset_dir/.${asset}.XXXXXX")"
    trap 'rm -f -- "$temporary"' EXIT
    curl --fail --location --retry 3 --max-time 120 \
        "https://github.com/Loyalsoldier/v2ray-rules-dat/releases/download/$release/$asset" \
        --output "$temporary"
    printf '%s  %s\n' "$expected" "$temporary" | sha256sum --check --status
    test "$(wc -c < "$temporary")" -gt 65536
    mv -- "$temporary" "$asset_dir/$asset"
    trap - EXIT
done
