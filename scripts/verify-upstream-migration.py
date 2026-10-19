#!/usr/bin/env python3
"""Check product boundaries and build inputs before either edition's migration build."""
from pathlib import Path
import re
import subprocess
import tomllib
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parent.parent
APP = ROOT / "V2rayNG/app"
SOURCE = APP / "src/main/java/com/v2ray/ang"
build = (APP / "build.gradle.kts").read_text()

def require(condition, message):
    if not condition:
        raise SystemExit(message)

require(re.search(r'applicationId = "com\.v2ray\.(mobiletina|alimtp)"', build), "Edition applicationId changed")
for expected in ('versionName = "2.3.12"', 'versionCode = 752', 'compose = true', 'viewBinding = true', 'isMinifyEnabled = true', 'isShrinkResources = true'):
    require(expected in build, f"Missing build contract: {expected}")

manifest = ET.parse(APP / "src/main/AndroidManifest.xml").getroot()
android = "{http://schemas.android.com/apk/res/android}"
activities = manifest.find("application").findall("activity")
launcher = next(a for a in activities if a.get(android + "name") == ".ui.MainActivity")
require(launcher.get(android + "theme") == "@style/MobileTinaLegacyTheme.NoActionBar", "Product launcher lost its Material theme")
require(any(a.get(android + "name") == ".ui.SubSettingActivity" for a in activities), "Hidden subscriptions screen missing")

contracts = {
    "ui/MainActivity.kt": ("smartConnectAndStart", "stopServiceReliably", "MANUAL_SUBSCRIPTION_HOLD_MS = 10_000L", "MobileTinaFirstLaunchDialog.showOnce", "import com.v2ray.ang.ui.server.ServerWireguardActivity"),
    "core/CoreConfigManager.kt": ("GeoIpRuleResolver.normalizeCustomRouting",),
    "core/CoreOutboundBuilder.kt": ("WireguardReservedPolicy.outboundBytes", "wireguard.remoteDNS = remotes", "AmneziaWGOptionsBean"),
    "core/CoreServiceManager.kt": ("nativeCleanup.get(15, TimeUnit.SECONDS)", "refreshAmneziaBindings", "networkMonitor === monitor"),
    "service/CoreVpnService.kt": ("VpnTeardownCoordinator", "closeVpnInterface = ::closeVpnInterface", "setupScope.cancel()"),
    "service/CoreTestService.kt": ("SMART_CONNECT_MAX_CONCURRENCY = 8", "batchGeneration", "message.onlyTcp"),
    "service/RealPingWorkerService.kt": ("RealPingExecutionLimiter", "secondaryUrl"),
    "handler/V2RayServiceManager.kt": ("LauncherManager.startService", "reconcileRunningState"),
    "handler/MobileTinaSubscriptionInfo.kt": ("item.requestHeaders", "subscription-userinfo"),
}
for relative, required in contracts.items():
    text = (SOURCE / relative).read_text()
    for expected in required:
        require(expected in text, f"{relative}: lost {expected}")

with (ROOT / "V2rayNG/gradle/libs.versions.toml").open("rb") as handle:
    catalog = tomllib.load(handle)
for name, library in catalog["libraries"].items():
    version = library.get("version")
    if isinstance(version, dict):
        require(version["ref"] in catalog["versions"], f"Missing dependency version: {name}")

for path in (APP / "src").rglob("*.xml"):
    ET.parse(path)
for path in SOURCE.rglob("*.kt"):
    require(not re.search(r"^(<<<<<<<|=======|>>>>>>>)", path.read_text(), re.M), f"Unresolved conflict: {path}")

for relative in ("ui/BaseActivity.kt", "ui/MainActivity.kt", "ui/SubSettingActivity.kt"):
    require((SOURCE / relative).is_file(), f"Retained product screen missing: {relative}")
print("Migration boundaries, custom behavior contracts, XML and dependency references verified.")
