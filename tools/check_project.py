"""Offline source integrity checks; not a substitute for Android SDK build/tests."""
import pathlib
import re
import xml.etree.ElementTree as ET

base = pathlib.Path(__file__).resolve().parent.parent
manifest = ET.parse(base / 'app/src/main/AndroidManifest.xml')
name = '{http://schemas.android.com/apk/res/android}name'
uses = {p.get(name) for p in manifest.findall('uses-permission')}
assert 'android.permission.RECORD_AUDIO' in uses
assert 'android.permission.FOREGROUND_SERVICE_MICROPHONE' in uses
assert 'android.permission.SYSTEM_ALERT_WINDOW' in uses
services = [s.get(name) for s in manifest.findall('.//service')]
assert '.speech.RecognitionService' in services
assert '.service.SubtitleOverlayService' in services
kts = (base / 'app/build.gradle.kts').read_text()
assert 'targetSdk = 35' in kts
assert '2.2.20' in (base / 'build.gradle.kts').read_text()
assert 'com.google.mlkit:translate:17.0.3' in kts
files = list((base / 'app/src/main/java').rglob('*.kt'))
assert len(files) >= 8
assert 'setup-gradle' in (base / '.github/workflows/android-apk.yml').read_text()
assert 'assembleDebug' in (base / '.github/workflows/android-apk.yml').read_text()
assert 'no access token' not in str(base)
for file in files:
    code = file.read_text()
    assert not re.search(r'(?i)sk-[a-z0-9]{20,}', code), f'Possible API key in {file}'
    assert not re.search(r'http://[^"\s]+', code), f'HTTP endpoint in {file}'
print(f'PASS source checks: {len(files)} Kotlin files, Android manifest parsed, Actions workflow configured, no plaintext API keys')
print('NOT RUN: Android compilation, Android UI/instrumented tests, device microphone/AI service tests')
