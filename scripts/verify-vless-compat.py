#!/usr/bin/env python3
"""Bind native VLESS regression evidence to the cached AAR and its source inputs."""
from pathlib import Path
import hashlib
import json
import sys
import zipfile

ROOT = Path(__file__).resolve().parents[1]
REPORTS = ROOT / "build/native-tests"
BASELINE_ERROR = "vless without TLS or other encryption is prohibited"
CONFIG_TEST = "TestMobileTinaVlessCompatibility"
ROUND_TRIP = "TestMobileTinaPlainVlessTCPRoundTrip"


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def events(path):
    return [json.loads(line) for line in path.read_text().splitlines() if line.strip()]


def check_baseline():
    rows = events(REPORTS / "before.jsonl")
    assert any(r.get("Test") == CONFIG_TEST + "/empty-security" and r["Action"] == "fail" for r in rows), "baseline must fail the reported config case"
    assert BASELINE_ERROR in "".join(r.get("Output", "") for r in rows), "baseline failed for a different reason"


def passed_tests():
    rows = events(REPORTS / "after.jsonl")
    assert not any(r["Action"] in ("fail", "skip") for r in rows), "native regression failed or skipped"
    passed = sorted(r["Test"] for r in rows if r["Action"] == "pass" and "Test" in r)
    required = {CONFIG_TEST, ROUND_TRIP} | {CONFIG_TEST + "/" + name for name in (
        "empty-security", "omitted-security", "explicit-none", "legacy-vnext", "public-ipv4", "public-ipv6",
        "tls-still-accepted", "invalid-encryption-still-rejected", "invalid-flow-still-rejected", "trojan-restriction-preserved",
    )}
    assert required <= set(passed), "native regression evidence is incomplete"
    return passed


def inputs():
    paths = [*sorted((ROOT / "core/patches").glob("*.patch")),
             *sorted((ROOT / "core/vless-compat").glob("*.go")),
             *sorted((ROOT / "core/mobile-recovery").glob("*.go")),
             ROOT / "scripts/build-amnezia-libv2ray.sh", Path(__file__).resolve(),
             ROOT / "AndroidLibXrayLite/go.mod", ROOT / "AndroidLibXrayLite/go.sum"]
    return {str(path.relative_to(ROOT)): digest(path) for path in paths}


def evidence(aar):
    check_baseline()
    with zipfile.ZipFile(aar) as archive:
        native = {name: hashlib.sha256(archive.read(name)).hexdigest() for name in archive.namelist() if name.endswith("/libgojni.so")}
    assert {name.split("/")[1] for name in native} == {"arm64-v8a", "armeabi-v7a"}
    return {"aar_sha256": digest(aar), "inputs_sha256": inputs(), "native_libraries_sha256": native,
            "before_sha256": digest(REPORTS / "before.jsonl"), "after_sha256": digest(REPORTS / "after.jsonl"),
            "passed_tests": passed_tests()}


if __name__ == "__main__":
    mode = sys.argv[1]
    if mode == "baseline":
        check_baseline()
        print("Reproduced the original plaintext VLESS rejection on the unmodified core")
    elif mode in ("record", "verify"):
        aar = Path(sys.argv[2]).resolve()
        current = evidence(aar)
        proof = REPORTS / "proof.json"
        if mode == "record":
            proof.write_text(json.dumps(current, indent=2) + "\n")
        else:
            assert json.loads(proof.read_text()) == current, "native AAR/source/test provenance mismatch"
        print(f"VLESS native compatibility {mode}: {len(current['passed_tests'])} passing test entries, both ARM ABIs")
    else:
        raise SystemExit("usage: verify-vless-compat.py baseline | record AAR | verify AAR")
