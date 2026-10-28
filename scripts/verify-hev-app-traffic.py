#!/usr/bin/env python3
"""Bind original-source metadata support to inputs and packaged ELF code/data."""
from pathlib import Path
import hashlib
import importlib.util
import json
import subprocess
import sys
import zipfile
ROOT = Path(__file__).resolve().parents[1]
REPORTS = ROOT / "build/hev-tests"
spec = importlib.util.spec_from_file_location("packaged_native", ROOT / "scripts/verify-packaged-native.py")
elf = importlib.util.module_from_spec(spec)
spec.loader.exec_module(elf)
def sha(data): return hashlib.sha256(data).hexdigest()
def inputs():
    paths=[ROOT/"compile-hevtun.sh",ROOT/"core/patches/hev-app-traffic.patch",Path(__file__).resolve(),ROOT/"scripts/verify-packaged-native.py",*sorted((ROOT/"core/app-traffic/hev").glob("*"))]
    return {str(p.relative_to(ROOT)):sha(p.read_bytes()) for p in paths if p.is_file()}
def main():
    mode,target=sys.argv[1],Path(sys.argv[2]);proof_file=REPORTS/"proof.json"
    if mode=="record":
        REPORTS.mkdir(parents=True,exist_ok=True);libraries={}
        for abi in ("armeabi-v7a","arm64-v8a","x86","x86_64"):
            for name in ("libhev-socks5-tunnel.so","libhevsockstun.so"):
                data=(target/abi/name).read_bytes()
                if name=="libhev-socks5-tunnel.so": assert b"TProxyGetSource" in data,"source JNI bridge missing"
                libraries[f"lib/{abi}/{name}"]={"sha256":sha(data),"elf_profile":elf.elf_profile(data)}
        pin=subprocess.check_output(["git","ls-files","--stage","hev-socks5-tunnel"],cwd=ROOT,text=True).split()[1]
        proof_file.write_text(json.dumps({"inputs_sha256":inputs(),"upstream_sha":pin,"libraries":libraries},indent=2)+"\n")
    elif mode=="verify":
        proof=json.loads(proof_file.read_text());assert proof["inputs_sha256"]==inputs(),"stale HEV source cache"
        result={"apk_sha256":sha(target.read_bytes()),"proof_sha256":sha(proof_file.read_bytes()),"libraries":{}}
        with zipfile.ZipFile(target) as archive:
            for abi in ("armeabi-v7a","arm64-v8a"):
                for name in ("libhev-socks5-tunnel.so","libhevsockstun.so"):
                    path=f"lib/{abi}/{name}";data=archive.read(path);expected=proof["libraries"][path]
                    assert elf.elf_profile(data)==expected["elf_profile"],f"HEV APK code/data mismatch: {path}"
                    result["libraries"][path]={"apk_sha256":sha(data),"source_library_sha256":expected["sha256"]}
        (REPORTS/"packaged-hev.json").write_text(json.dumps(result,indent=2)+"\n")
    else: raise SystemExit("expected record LIBS or verify APK")
    print(f"HEV original-source bridge provenance {mode}: passed")
if __name__=="__main__": main()
