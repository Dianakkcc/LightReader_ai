"""Validate repository inputs before packaging. Standard library only."""
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[2]
required = ['.gitignore', '.gitattributes', '.github/workflows/android.yml',
            'gradlew', 'gradle/wrapper/gradle-wrapper.jar', 'docs/MIGRATION.md']
for name in required:
    assert (root / name).is_file(), f'Missing required file: {name}'
assert b'\r' not in (root / 'gradlew').read_bytes(), 'gradlew must use LF'
assert 'gradlew text eol=lf' in (root / '.gitattributes').read_text(encoding='utf-8')
resources = []
for qualifier in ['values', 'values-en']:
    items = ET.parse(root / f'app/src/main/res/{qualifier}/strings.xml').getroot()
    names = [item.attrib['name'] for item in items if item.tag == 'string']
    assert len(names) == len(set(names)), f'Duplicate string keys: {qualifier}'
    resources.append(set(names))
assert resources[0] == resources[1], 'Chinese and English string keys differ'
gradle = (root / 'app/build.gradle.kts').read_text(encoding='utf-8')
assert 'abortOnError = true' in gradle
assert 'Android Debug' in gradle, 'Expected release signing guard'
logs = (root / 'app/src/main/java/com/lightreader/core/util/Logs.kt').read_text(encoding='utf-8')
assert 'BuildConfig.DEBUG' in logs
web = (root / 'app/src/main/java/com/lightreader/ui/reader/WebReaderView.kt').read_text(encoding='utf-8')
assert 'runBlocking' not in web and 'withTimeoutOrNull' not in web
assert '资源送达' not in web
manifest = (root / 'app/src/main/AndroidManifest.xml').read_text(encoding='utf-8')
assert 'android.permission.INTERNET' not in manifest
print(f'Source checks passed; {len(resources[0])} matching bilingual string keys.')
