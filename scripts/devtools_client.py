"""Small HTTP client for operating the development instance without desktop automation."""
import hashlib
import json
from pathlib import Path
import shutil
import time
import urllib.request

ROOT = Path(__file__).resolve().parents[1]

class DevClient:
    def __init__(self, build_id):
        assert build_id.replace('-', '').replace('_', '').isalnum()
        self.build_id = build_id
        self.out = ROOT / 'evidence' / build_id
        self.out.mkdir(parents=True, exist_ok=False)
        self.connection = json.loads((ROOT / 'run-devtools/devtools/connection.json').read_text())
        digest = hashlib.sha256()
        for source in sorted((ROOT / 'src').rglob('*')):
            if source.is_file():
                digest.update(source.relative_to(ROOT).as_posix().encode())
                digest.update(source.read_bytes())
        self.records = {'status': 'running', 'source_sha256': digest.hexdigest(), 'steps': {}}

    def request(self, action=None):
        req = urllib.request.Request(self.connection['url'] + ('/action' if action else '/state'),
            data=json.dumps(action).encode() if action else None,
            headers={'X-Ori-Devtools': self.connection['token'], 'Content-Type': 'application/json'})
        with urllib.request.urlopen(req, timeout=55) as response:
            return json.load(response)

    def action(self, op, **kwargs):
        return self.request({'op': op, **kwargs})

    def command(self, text):
        return self.action('command', command=text)

    def spirit(self):
        return self.action('spirit_state')

    def ticks(self, ticks):
        target = self.request()['tick'] + ticks
        deadline = time.monotonic() + max(10, ticks / 10)
        while self.request()['tick'] < target:
            if time.monotonic() > deadline:
                raise TimeoutError('Client ticks stalled')
            time.sleep(.008)

    def ready(self):
        deadline = time.monotonic() + 60
        while time.monotonic() < deadline:
            state = self.request()
            if state['screen'] == 'gameplay' and state['overlay'] == 'none' and 'player' in state:
                assert state['windowActive'], 'Input must use the real focus guard'
                return state
            time.sleep(.2)
        raise TimeoutError('World did not become ready')

    def reset(self, x=.5, y=100, z=.5, yaw=0, pitch=0):
        self.action('release_keys')
        self.command(f'tp @s {x} {y} {z} {yaw} {pitch}')
        self.command('effect give @s minecraft:instant_health 1 5 true')
        self.ticks(6)

    def key(self, name, ticks):
        self.action('key', name='key.' + name, ticks=ticks)

    def start(self):
        self.action('record_motion')
        return self.spirit()

    def finish(self, name, before, **extra):
        after = self.spirit()
        result = self.action('save_motion', name=self.build_id + '-' + name)
        shutil.copy2(result['path'], self.out / (name + '.json'))
        self.records['steps'][name] = {'before': before, 'after': after, 'samples': result['samples'], **extra}
        self.save()
        return after

    def screenshot(self, name):
        result = self.action('screenshot', name=self.build_id + '-' + name)
        shutil.copy2(result['path'], self.out / (name + '.png'))
        return result

    def save(self):
        (self.out / 'results.json').write_text(json.dumps(self.records, ensure_ascii=False, indent=2), encoding='utf-8')
