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

# A silent test must use a regular service at both ends; a foreground-service
# launcher without startForeground would crash on Android 8+ after its deadline.
test_service = (SOURCE / "service/CoreTestService.kt").read_text()
test_dispatch = (SOURCE / "helper/MessageHelper.kt").read_text().split("fun sendMsg2TestService(", 1)[1].split("fun sendMsg2SubscriptionService(", 1)[0]
require("ctx.startService(intent)" in test_dispatch, "Tests must start a regular service")
require("ContextCompat.startForegroundService" not in test_dispatch, "Test launcher would require a notification")
require("NotificationHelper" not in test_service, "Test notifications must remain disabled")
test_manifest = next(s for s in manifest.find("application").findall("service") if s.get(android + "name") == ".service.CoreTestService")
require(test_manifest.get(android + "foregroundServiceType") is None, "Test service must not declare a foreground type")
for path in SOURCE.rglob("*.kt"):
    text = path.read_text()
    for removed in ("PREF_IS_BOOTED", "decodeStartOnBoot", "encodeStartOnBoot", "APP_WIKI_MODE", "ACTION_VPN_SETTINGS", "onModeHelpClicked", "systemVpnSettingsAvailable"):
        require(removed not in text, f"Removed settings behavior remains in {path}: {removed}")
boot = (SOURCE / "receiver/BootReceiver.kt").read_text()
require("MobileTinaExpiryManager.recoverPending" in boot, "Boot expiry recovery must survive startup-setting removal")
require("LauncherManager" not in boot, "Boot receiver must not start a VPN")
config = (SOURCE / "AppConfig.kt").read_text()
for setting in ("SNIFFING_ENABLED", "DYNAMIC_COLOR"):
    require(f"const val DEFAULT_{setting} = false" in config, f"{setting} must default to disabled")
    for path in SOURCE.rglob("*.kt"):
        text = path.read_text()
        require(not re.search(rf"(?:decodeSettingsBool|rememberMmkvBool)\(AppConfig.PREF_{setting},\s*true\)", text), f"Enabled fallback for {setting}: {path}")
for path in (APP / "src").rglob("*.xml"):
    text = path.read_text()
    for removed in ("title_pref_is_booted", "summary_pref_is_booted", "title_system_vpn_settings", "summary_system_vpn_settings", "toast_system_vpn_settings_unavailable", "title_mode_help"):
        require(removed not in text, f"Removed settings resource remains in {path}: {removed}")
night_colors = {c.get("name"): c.text for c in ET.parse(APP / "src/main/res/values-night/colors.xml").getroot()}
for name in ("md_theme_background", "md_theme_surface"):
    require(night_colors[name] == "#000000", f"OLED night resource must be black: {name}")
theme = (SOURCE / "ui/compose/Theme.kt").read_text()
require("if (darkTheme) palette.withOledSurfaces() else palette" in theme, "OLED must also cover manually enabled dynamic dark colors")

with (ROOT / "V2rayNG/gradle/libs.versions.toml").open("rb") as handle:
    catalog = tomllib.load(handle)
for name, library in catalog["libraries"].items():
    version = library.get("version")
    if isinstance(version, dict):
        require(version["ref"] in catalog["versions"], f"Missing dependency version: {name}")

for path in (APP / "src").rglob("*.xml"):
    ET.parse(path)
resource_types = {}
for path in (APP / "src/main/res/values").glob("*.xml"):
    for item in ET.parse(path).getroot():
        name = item.get("name")
        if name:
            resource_types.setdefault(name, set()).add(item.tag)
for path in SOURCE.rglob("*.kt"):
    text = path.read_text()
    require(not re.search(r"^(<<<<<<<|=======|>>>>>>>)", text, re.M), f"Unresolved conflict: {path}")
    for kind, name in re.findall(r"(?<![.\w])R\.(string|plurals)\.(\w+)", text):
        if name in resource_types:
            require(kind in resource_types[name], f"{path}: {name} must be declared as {kind}")

for relative in ("ui/BaseActivity.kt", "ui/MainActivity.kt", "ui/SubSettingActivity.kt"):
    require((SOURCE / relative).is_file(), f"Retained product screen missing: {relative}")
print("Migration boundaries, custom behavior contracts, XML and dependency references verified.")
