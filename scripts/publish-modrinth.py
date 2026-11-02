"""Upload a release jar to Modrinth as a new version.

Usage: python3 scripts/publish-modrinth.py <jar> [--dry-run]
Env: MODRINTH_TOKEN (PAT with "Create versions"), MODRINTH_PROJECT_ID.
Version number, changelog and channel come from gradle.properties and CHANGELOG.md; GemRender and
Flywheel are nested in the jar, so only Iris and Sodium are listed (optional). Uses API v3, the only one that
takes the per-version environment; multiplayer is not supported yet, hence singleplayer_only.
"""
import json, os, re, sys, uuid, urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
IRIS, SODIUM = 'YL57xq9U', 'AANobbMI'  # Modrinth project ids, as in dependencies/lock.json

def prop(name):
    return re.search(rf'^{name}=(.*)$', (ROOT / 'gradle.properties').read_text(), re.M).group(1).strip()

def changelog(version):
    text = (ROOT / 'CHANGELOG.md').read_text(encoding='utf-8')
    match = re.search(rf'^## {re.escape(version)}\b.*?$(.*?)(?=^## |\Z)', text, re.M | re.S)
    if not match: raise SystemExit(f'CHANGELOG.md has no "## {version}" section')
    return match.group(1).strip()

def main():
    args = [a for a in sys.argv[1:] if not a.startswith('--')]
    if len(args) != 1: raise SystemExit(__doc__)
    jar = Path(args[0])
    version = prop('mod_version')
    channel = 'alpha' if 'alpha' in version else 'beta' if 'beta' in version else 'release'
    data = {
        'project_id': os.environ.get('MODRINTH_PROJECT_ID', ''),
        'name': f'Glimmerwood {version}',  # public name; mod_name stays OriMod in game
        'version_number': version,
        'changelog': changelog(version),
        'version_type': channel,
        'game_versions': [prop('minecraft_version')],
        'loaders': ['neoforge'],
        'environment': 'singleplayer_only',
        'featured': True,
        'dependencies': [{'project_id': IRIS, 'dependency_type': 'optional'},
                         {'project_id': SODIUM, 'dependency_type': 'optional'}],
        'file_parts': ['file'],
        'primary_file': 'file',
    }
    if '--dry-run' in sys.argv:
        print(json.dumps(data, ensure_ascii=False, indent=2)); return
    if not data['project_id'] or not os.environ.get('MODRINTH_TOKEN'):
        raise SystemExit('MODRINTH_TOKEN and MODRINTH_PROJECT_ID are required')
    boundary = uuid.uuid4().hex
    body = b''.join([
        f'--{boundary}\r\nContent-Disposition: form-data; name="data"\r\nContent-Type: application/json\r\n\r\n'.encode(),
        json.dumps(data).encode(), b'\r\n',
        f'--{boundary}\r\nContent-Disposition: form-data; name="file"; filename="{jar.name}"\r\n'
        'Content-Type: application/java-archive\r\n\r\n'.encode(),
        jar.read_bytes(), f'\r\n--{boundary}--\r\n'.encode()])
    request = urllib.request.Request('https://api.modrinth.com/v3/version', data=body, method='POST', headers={
        'Authorization': os.environ['MODRINTH_TOKEN'],
        'Content-Type': f'multipart/form-data; boundary={boundary}',
        'User-Agent': 'wwlWang/glimmerwood release script (github.com/wwlWang/glimmerwood)'})
    with urllib.request.urlopen(request, timeout=120) as response:
        result = json.load(response)
    print(f'Modrinth version {result["version_number"]} uploaded: {result["id"]}')

main()
