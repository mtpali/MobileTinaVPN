#!/usr/bin/env python3
"""Reject reintroduced per-app usage UI, collectors, bridges or cached native bindings."""
from pathlib import Path
import hashlib
import json
import sys
import zipfile

ROOT = Path(__file__).resolve().parents[1]
TOKENS = (b'AppTrafficLog', b'AppTrafficRecorder', b'APP_TRAFFIC_LOG_',
          b'startAppTrafficLog', b'TProxyGetSource', b'hev_app_traffic_',
          b'mobiletina_app_traffic', b'PACKAGE_USAGE_STATS', b'NetworkStatsManager')

def check(data, label):
    for token in TOKENS:
        assert token not in data and token.decode().encode('utf-16le') not in data, (label, token)

sources = []
for directory in (ROOT / 'V2rayNG/app/src/main', ROOT / 'core'):
    for path in sorted(directory.rglob('*')):
        if path.is_file() and path.suffix in ('.kt', '.java', '.xml', '.go', '.c', '.h', '.patch'):
            check(path.read_bytes(), str(path.relative_to(ROOT)))
            sources.append(str(path.relative_to(ROOT)))
assert not (ROOT / 'core/app-traffic').exists()
assert not (ROOT / 'V2rayNG/app/src/main/java/com/v2ray/ang/ui/traffic/AppTrafficLogActivity.kt').exists()
result = {'source_files_checked': len(sources), 'per_app_usage_feature_absent': True}
if len(sys.argv) > 1:
    apk = Path(sys.argv[1])
    checked = []
    with zipfile.ZipFile(apk) as archive:
        for name in archive.namelist():
            if name.endswith(('.dex', '.so')) or name in ('AndroidManifest.xml', 'resources.arsc'):
                check(archive.read(name), name)
                checked.append(name)
    result.update(apk_sha256=hashlib.sha256(apk.read_bytes()).hexdigest(), apk_payloads_checked=checked)
    output = ROOT / 'build/native-tests/no-usage-feature.json'
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(result, indent=2) + '\n')
print(json.dumps(result, indent=2))
