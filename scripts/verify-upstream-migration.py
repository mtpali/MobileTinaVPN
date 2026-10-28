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
for expected in ('versionName = "2.3.14"', 'versionCode = 754', 'compose = true', 'viewBinding = true', 'isMinifyEnabled = true', 'isShrinkResources = true'):
    require(expected in build, f"Missing build contract: {expected}")

# The target's exact native sources are part of the security update, not optional caches.
for path, expected in {
    "AndroidLibXrayLite": "76a6e2172210613fcb4aa32b6de55bcd9452ce43",
    "hev-socks5-tunnel": "c5584feca8f6df70ee53eb2879e034b849a3d15a",
}.items():
    entry = subprocess.check_output(["git", "ls-files", "--stage", path], cwd=ROOT, text=True)
    require(entry.startswith(f"160000 {expected} 0\t"), f"Incorrect 2.3.14 native pin: {path}")

manifest = ET.parse(APP / "src/main/AndroidManifest.xml").getroot()
android = "{http://schemas.android.com/apk/res/android}"
activities = manifest.find("application").findall("activity")
launcher = next(a for a in activities if a.get(android + "name") == ".ui.MainActivity")
require(launcher.get(android + "theme") == "@style/MobileTinaLegacyTheme.NoActionBar", "Product launcher lost its Material theme")
require(any(a.get(android + "name") == ".ui.SubSettingActivity" for a in activities), "Hidden subscriptions screen missing")

contracts = {
    "ui/MainActivity.kt": ("smartConnectAndStart", "stopServiceReliably", "SubscriptionHeaderHold(header, this", "installManualSubscriptionClicks", "MobileTinaFirstLaunchDialog.showOnce", "import com.v2ray.ang.ui.server.ServerWireguardActivity"),
    "core/CoreConfigManager.kt": ("GeoIpRuleResolver.normalizeCustomRouting",),
    "core/CoreOutboundBuilder.kt": ("WireguardReservedPolicy.outboundBytes", "wireguard.remoteDNS = remotes", "AmneziaWGOptionsBean"),
    "core/CoreServiceManager.kt": ("nativeCleanup.get(15, TimeUnit.SECONDS)", "refreshAmneziaBindings", "networkMonitor === monitor"),
    "service/CoreVpnService.kt": ("VpnTeardownCoordinator", "closeVpnInterface = ::closeVpnInterface", "setupScope.cancel()"),
    "service/CoreTestService.kt": ("SMART_CONNECT_MAX_CONCURRENCY = 8", "batchGeneration", "message.onlyTcp"),
    "service/RealPingWorkerService.kt": ("RealPingExecutionLimiter", "secondaryUrl"),
    "handler/V2RayServiceManager.kt": ("LauncherManager.startService", "reconcileRunningState"),
    "handler/MobileTinaSubscriptionInfo.kt": ("item.requestHeaders", "subscription-userinfo", "SubscriptionInfoParser.parse", "MobileTinaSubscriptionMetadata.apply"),
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
for setting in ("SNIFFING_ENABLED",):
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
require("if (darkTheme) palette.withOledSurfaces() else palette" in theme, "OLED must cover dark mode")
require(theme.count("AppCompatDelegate.setDefaultNightMode") == 2, "Stored and newly selected themes must update legacy activities")
require("ThemeModePolicy.isDark(mode, isSystemInDarkTheme())" in theme, "Compose and Views must share the theme policy")
for path in SOURCE.rglob("*.kt"):
    text = path.read_text()
    for removed in ("PREF_DYNAMIC_COLOR", "DEFAULT_DYNAMIC_COLOR", "dynamicDarkColorScheme", "dynamicLightColorScheme", "PREF_LANGUAGE", "setApplicationLanguage", "MANUAL_SUBSCRIPTION_HOLD_MS"):
        require(removed not in text, f"Removed appearance/language/hold behavior remains in {path}: {removed}")
settings = (SOURCE / "ui/settings/SettingsActivity.kt").read_text()
require(settings.index("R.string.title_pref_ui_mode_night") < settings.index("R.string.title_ui_settings"), "Theme selector must be the first settings row")
require('resourceConfigurations.add("en")' in build, "Only the original default English locale may be packaged")
locale_config = ET.parse(APP / "src/main/res/xml/locales_config.xml").getroot()
require([item.get(android + "name") for item in locale_config] == ["en"], "Advertise only the default locale")
for path in (APP / "src").rglob("*.xml"):
    require(not re.match(r"values-(?:[a-z]{2,3})(?:$|-r)", path.parent.name) or path.parent.name.startswith("values-en"), f"Additional locale source remains: {path}")
    for removed in ("title_language", "language_select", "title_pref_dynamic_color", "summary_pref_dynamic_color"):
        require(removed not in path.read_text(), f"Removed preference resource remains: {path}: {removed}")
core = (SOURCE / "core/CoreConfigManager.kt").read_text()
runtime, latency = core.split("fun getV2rayConfig4Speedtest(", 1)
require("IranDomainBypass.applyToRuntimeConfig" in runtime, "Runtime configs must apply the direct .ir policy")
require("IranDomainBypass.applyToRuntimeConfig" not in latency.split("private fun buildV2rayCustomConfig(", 1)[0], "Latency probes must still test the proxy")
require("IranDomainBypass.DOMAIN_RULE !in domain" in core, "Generated DNS must include .ir in direct domains")
require("0xFFf97910" not in theme, "The old orange Compose accent must not return")
overlay = ET.parse(APP / "src/playstore/AndroidManifest.xml").getroot()
widget = next(item for item in overlay.find("application").findall("receiver") if item.get(android + "name") == ".receiver.WidgetProvider")
tools_ns = "{http://schemas.android.com/tools}"
if 'applicationId = "com.v2ray.alimtp"' in build:
    require(widget.get(tools_ns + "node") == "merge" and widget.get(android + "enabled") == "true", "Personal VPN widget must be available")
    provider = (SOURCE / "receiver/WidgetProvider.kt").read_text()
    require("PendingIntent.getBroadcast(" in provider and "MainActivity" not in provider, "Widget must not open the product activity")
    require("startSmartService(context, toggle = true)" in provider, "Widget must use background Smart Connect")
    delegate = (SOURCE / "service/SmartConnectServiceDelegate.kt").read_text()
    require("service.startService(Intent(service, service.javaClass)" in delegate, "Selection must hand off to the same foreground service")
    require("intent?.getStringExtra(MessageHelper.EXTRA_REQUEST_ID) != probeRequestId" in delegate and
            "startedBatchId == batchId" in delegate, "Smart Connect IPC must correlate both request and batch")
    require("if (obsolete && owner == null && !busy &&" in delegate and "stopService()" in delegate, "An obsolete handoff must close an otherwise idle foreground service")
    for name in ("CoreVpnService", "CoreProxyOnlyService", "CoreRootService"):
        service = (SOURCE / f"service/{name}.kt").read_text()
        require(service.index("NotificationManager.ensureForeground()") < service.index("smartConnect.handleCommand("), f"{name} must enter foreground before selection")
        require("smartConnect.close()" in service, f"{name} must cancel selection on teardown")
else:
    require(widget.get(tools_ns + "node") == "remove", "Widget restoration is personal-edition only")

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
