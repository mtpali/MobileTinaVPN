#!/usr/bin/env python3
"""Compare tested and APK ELF code/data while allowing Android symbol stripping."""
from pathlib import Path
import hashlib
import json
import struct
import sys
import zipfile


def sha256(data):
    return hashlib.sha256(data).hexdigest()


def elf_profile(data):
    assert data[:4] == b"\x7fELF"
    elf_class, endian = data[4], data[5]
    assert elf_class in (1, 2) and endian in (1, 2)
    prefix = "<" if endian == 1 else ">"
    fmt = "HHIIIIIHHHHHH" if elf_class == 1 else "HHIQQQIHHHHHH"
    fields = struct.unpack_from(prefix + fmt, data, 16)
    kind, machine, version, entry, phoff, shoff, flags, ehsize, phsize, phnum, shsize, shnum, shstrndx = fields
    assert phnum and shnum and shstrndx < shnum
    section_fmt = prefix + ("IIIIIIIIII" if elf_class == 1 else "IIQQQQIIQQ")
    sections = [struct.unpack_from(section_fmt, data, shoff + i * shsize) for i in range(shnum)]
    strings = sections[shstrndx]
    names = data[strings[4]:strings[4] + strings[5]]
    allocated = {}
    for section in sections:
        name_offset, section_type, section_flags, address, offset, size, link, info, alignment, entsize = section
        if not section_flags & 2:  # SHF_ALLOC: loader-visible code/data only
            continue
        name = names[name_offset:names.index(0, name_offset)].decode("ascii")
        assert name not in allocated
        allocated[name] = {"type": section_type, "flags": section_flags, "address": address,
            "size": size, "alignment": alignment, "entry_size": entsize,
            "sha256": None if section_type == 8 else sha256(data[offset:offset + size])}
    assert ".text" in allocated and any(k.startswith(".data") for k in allocated)
    program_fmt = prefix + ("IIIIIIII" if elf_class == 1 else "IIQQQQQQ")
    program_headers = [list(struct.unpack_from(program_fmt, data, phoff + i * phsize)) for i in range(phnum)]
    return {"elf_class": elf_class, "endianness": endian, "osabi": data[7], "abi_version": data[8],
        "type": kind, "machine": machine, "version": version, "entry": entry, "flags": flags,
        "program_headers": program_headers, "allocated_sections": allocated}


def verify(aar, apk, output):
    root = Path(__file__).resolve().parents[1]
    native_proof = root / "build/native-tests/proof.json"
    proof = json.loads(native_proof.read_text())
    assert sha256(aar.read_bytes()) == proof["aar_sha256"]
    result = {"apk_sha256": sha256(apk.read_bytes()), "aar_sha256": proof["aar_sha256"],
        "native_proof_sha256": sha256(native_proof.read_bytes()),
        "verifier_sha256": sha256(Path(__file__).read_bytes()), "libraries": {}}
    with zipfile.ZipFile(aar) as source, zipfile.ZipFile(apk) as packaged:
        for abi in ("arm64-v8a", "armeabi-v7a"):
            original = source.read(f"jni/{abi}/libgojni.so")
            actual = packaged.read(f"lib/{abi}/libgojni.so")
            assert sha256(original) == proof["native_libraries_sha256"][f"jni/{abi}/libgojni.so"]
            expected_profile = elf_profile(original)
            assert elf_profile(actual) == expected_profile, f"packaged ELF code/data or loader metadata changed: {abi}"
            assert b"vless without TLS or other encryption is prohibited" not in actual
            assert b"trojan without TLS is prohibited" in actual
            result["libraries"][abi] = {"aar_library_sha256": sha256(original), "apk_library_sha256": sha256(actual),
                "whole_file_equal": original == actual, "elf_profile": expected_profile}
    output.write_text(json.dumps(result, indent=2) + "\n")
    print("Both packaged ARM libraries retain the tested ELF code, data and loader metadata")


if __name__ == "__main__":
    verify(Path(sys.argv[1]), Path(sys.argv[2]), Path(sys.argv[3]))
