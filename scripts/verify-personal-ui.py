#!/usr/bin/env python3
"""Verify the personal flavor's drawer artwork and Persian toast contract."""

from __future__ import annotations

import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
JAVA_ROOT = ROOT / "V2rayNG/app/src/main/java"
BASE_VALUES = ROOT / "V2rayNG/app/src/main/res/values"
TOAST_OVERRIDES = (
    ROOT / "V2rayNG/app/src/playstore/res/values/mobiletina_toast_overrides.xml"
)
NAV_LAYOUT = ROOT / "V2rayNG/app/src/main/res/layout/nav_header.xml"
PERSIAN = re.compile(r"[\u0600-\u06ff]")


def read_strings(path: Path) -> dict[str, str]:
    root = ET.parse(path).getroot()
    return {
        element.attrib["name"]: "".join(element.itertext()).strip()
        for element in root.findall("string")
    }


def fail(messages: list[str]) -> None:
    for message in messages:
        print(f"ERROR: {message}", file=sys.stderr)
    raise SystemExit(1)


def main() -> None:
    errors: list[str] = []
    base: dict[str, str] = {}
    for values_file in BASE_VALUES.glob("*.xml"):
        base.update(read_strings(values_file))
    overrides = read_strings(TOAST_OVERRIDES)

    base_toasts = {name for name in base if name.startswith("toast_")}
    missing_toasts = sorted(base_toasts - overrides.keys())
    if missing_toasts:
        errors.append("missing playstore toast overrides: " + ", ".join(missing_toasts))

    sources = "\n".join(path.read_text() for path in JAVA_ROOT.rglob("*.kt"))
    resource_pattern = re.compile(
        r"(?:toast|toastSuccess|toastError|toastWarning)\s*\(\s*"
        r"(?:getString\s*\(\s*)?R\.string\.(\w+)"
    )
    referenced = set(resource_pattern.findall(sources))
    referenced.add("toast_permission_denied")

    for name in sorted(referenced | base_toasts):
        text = overrides.get(name, base.get(name, ""))
        if not text:
            errors.append(f"toast resource has no text: {name}")
        elif not PERSIAN.search(text):
            errors.append(f"toast resource is not Persian: {name}={text!r}")

    if re.search(
        r"(?:toast|toastSuccess|toastError|toastWarning)\s*\(\s*[\"']else[\"']",
        sources,
    ):
        errors.append("placeholder English text is shown in a toast")

    literal_pattern = re.compile(
        r"(?:toast|toastSuccess|toastError|toastWarning)\s*\(\s*[\"']([^\"']+)[\"']"
    )
    for literal in literal_pattern.findall(sources):
        # Interpolated exception messages intentionally preserve the original system/core error.
        if "${" not in literal and not PERSIAN.search(literal):
            errors.append(f"non-Persian literal toast: {literal!r}")

    permission_source = (
        JAVA_ROOT / "com/v2ray/ang/enums/PermissionType.kt"
    ).read_text()
    for english_label in ("Camera", "Notification", "Local Network"):
        if f'-> "{english_label}"' in permission_source:
            errors.append(f"permission toast label is still English: {english_label}")

    nav_root = ET.parse(NAV_LAYOUT).getroot()
    android = "{http://schemas.android.com/apk/res/android}"
    if nav_root.attrib.get(android + "layout_height") != "161dp":
        errors.append("drawer header must preserve the 600:335 artwork ratio at 288dp width")
    if any(name.startswith(android + "padding") for name in nav_root.attrib):
        errors.append("drawer header must not add padding around nav.webp")
    image = nav_root.find("ImageView")
    if image is None or image.attrib.get(android + "scaleType") != "fitCenter":
        errors.append("drawer artwork must use fitCenter to avoid cropping or stretching")

    if errors:
        fail(errors)

    print(
        f"Personal UI verified: {len(referenced)} referenced toast resources and "
        f"{len(base_toasts)} toast definitions are Persian; nav artwork fits without cropping."
    )


if __name__ == "__main__":
    main()
