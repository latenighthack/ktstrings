#!/usr/bin/env python3
"""Build, inspect, and run the installed shrunk Android fixture."""
from pathlib import Path
import os
import shutil
import subprocess
import zipfile

root = Path(__file__).resolve().parents[1]
fixture = root / 'integration/android'
sdk = Path(os.environ.get('ANDROID_HOME', os.environ.get('ANDROID_SDK_ROOT', str(Path.home() / 'Library/Android/sdk'))))
adb = shutil.which('adb') or str(sdk / 'platform-tools/adb')

def run(*args):
    print('+', ' '.join(map(str, args)), flush=True)
    return subprocess.check_output(list(map(str, args)), text=True, cwd=root, stderr=subprocess.STDOUT)

print(run(root/'gradlew', '-p', fixture, ':catalog:assembleRelease', ':catalogReverse:assembleRelease', ':app:assembleRelease', ':app:bundleRelease', ':app:assembleReleaseAndroidTest', '--console=plain'))
aar = fixture/'catalog/build/outputs/aar/catalog-release.aar'
apk = fixture/'app/build/outputs/apk/release/app-release.apk'
aab = fixture/'app/build/outputs/bundle/release/app-release.aab'
with zipfile.ZipFile(aar) as archive:
    names = archive.namelist()
    assert any(name.startswith('res/values') and name.endswith('.xml') for name in names)
    xml = '\n'.join(archive.read(name).decode() for name in names if name.startswith('res/values') and name.endswith('.xml'))
    assert 'ktstrings_app_items_count' in xml and 'ktstrings_app_welcome' in xml
    assert not any(name.endswith('catalog.json') for name in names)
with zipfile.ZipFile(apk) as archive:
    assert 'resources.arsc' in archive.namelist()
    assert not any(name.endswith('catalog.json') for name in archive.namelist())
with zipfile.ZipFile(aab) as archive:
    assert b'ktstrings_app_items_count' in archive.read('base/resources.pb')
assert (fixture/'app/build/outputs/mapping/release/mapping.txt').is_file(), 'R8 did not run'
aapt = sorted((sdk/'build-tools').glob('*/aapt2'))[-1]
resources = run(aapt, 'dump', 'resources', apk)
assert 'ktstrings_app_items_count' in resources
print('AAR, shrunk APK, and AAB contain native catalog resources; source catalogs are absent.')
serial = os.environ.get('ANDROID_SERIAL')
if not serial:
    devices = [line.split()[0] for line in run(adb, 'devices').splitlines()[1:] if line.endswith('\tdevice')]
    if not devices:
        raise SystemExit('Start an Android emulator or set ANDROID_SERIAL to run installed-package tests.')
    serial = devices[0]
print(run(adb, '-s', serial, 'install', '-r', apk))
print(run(adb, '-s', serial, 'install', '-r', fixture/'app/build/outputs/apk/androidTest/release/app-release-androidTest.apk'))
result = run(adb, '-s', serial, 'shell', 'am', 'instrument', '-w', 'fixture.ktstrings.test/androidx.test.runner.AndroidJUnitRunner')
print(result)
assert 'OK (' in result and 'FAILURES' not in result, 'Installed native/Compose tests failed'
print('Installed native localization and Compose locale-change acceptance passed.')
