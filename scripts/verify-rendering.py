"""Operate the development client and retain evidence; screenshots still need visual review."""
import argparse
import json
from pathlib import Path
import shutil
import time
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser()
parser.add_argument('build_id')
parser.add_argument('--benchmark-seconds', type=int, default=60)
args = parser.parse_args()
assert args.build_id.replace('-', '').replace('_', '').isalnum()
assert 5 <= args.benchmark_seconds <= 120
evidence = ROOT / 'evidence' / args.build_id
evidence.mkdir(parents=True, exist_ok=False)
connection = json.loads((ROOT / 'run-devtools/devtools/connection.json').read_text())

def request(action=None):
    req = urllib.request.Request(connection['url'] + ('/action' if action else '/state'),
        data=json.dumps(action).encode() if action else None,
        headers={'X-Ori-Devtools': connection['token'], 'Content-Type': 'application/json'})
    with urllib.request.urlopen(req, timeout=55) as response:
        return json.load(response)

def wait_ready(predicate=lambda s: True, timeout=50):
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        state = request()
        assert not state['reloadError'], state['reloadError']
        if state['overlay'] == 'none' and state['screen'] == 'gameplay' and predicate(state):
            return state
        time.sleep(0.5)
    raise TimeoutError(f'Client condition not met: {state}')

def snapshot(name):
    result = request({'op': 'screenshot', 'name': args.build_id + '-' + name})
    shutil.copy2(result['path'], evidence / (name + '.png'))
    return result['state']

initial = wait_ready()
assert initial['shaders']['enabled'] and initial['characterPreview']['liveVisuals'] == 1
assert initial['effectPreview']['active']
records = {'initial': initial, 'steps': [], 'visual_review': 'pending'}
(evidence / 'state.json').write_text(json.dumps(records, indent=2))
for index in range(1, 6):
    before = request()
    request({'op': 'reload_resources'})
    state = wait_ready(lambda s: s['resourceReloads'] == before['resourceReloads'] + 1
        and s['characterPreview']['modelGenerations'] > before['characterPreview']['modelGenerations']
        and s['effectPreview']['active'])
    assert state['characterPreview']['liveVisuals'] == 1
    time.sleep(2)
    records['steps'].append({'step': f'reload-{index}', 'state': snapshot(f'reload-{index}')})
    (evidence / 'state.json').write_text(json.dumps(records, indent=2))
    print(f'reload {index}/5: one visual, effect restored, no reload error', flush=True)

for width, height in [(1280, 720), (1920, 1080)]:
    request({'op': 'resize', 'width': width, 'height': height})
    state = wait_ready(lambda s: s['framebuffer'] == {'width': width, 'height': height})
    time.sleep(2)
    assert state['characterPreview']['liveVisuals'] == 1 and state['effectPreview']['active']
    records['steps'].append({'step': f'resize-{width}', 'state': snapshot(f'resize-{width}')})
    print(f'resize {width}x{height}: framebuffer confirmed', flush=True)

request({'op': 'settings', 'fpsLimit': 260, 'vsync': False, 'renderDistance': 12, 'hideGui': True})
wait_ready()
time.sleep(3)
request({'op': 'record_frames'})
print(f'performance sampling for {args.benchmark_seconds}s without screenshots or recording', flush=True)
time.sleep(args.benchmark_seconds)
result = request({'op': 'save_frames', 'name': args.build_id + '-frame-times'})
shutil.copy2(result['path'], evidence / 'frame-times.json')
samples = json.loads((evidence / 'frame-times.json').read_text())['frameTimeMs']
ordered = sorted(samples)
def percentile(p):
    at = (len(ordered) - 1) * p
    lo = int(at)
    return ordered[lo] + (ordered[min(lo + 1, len(ordered) - 1)] - ordered[lo]) * (at - lo)
records['performance'] = {'samples': len(samples), 'seconds': sum(samples) / 1000,
    'average_fps': len(samples) * 1000 / sum(samples),
    'median_ms': percentile(0.5), 'p95_ms': percentile(0.95), 'p99_ms': percentile(0.99),
    'max_ms': max(samples), 'over_50ms': sum(x > 50 for x in samples)}
records['final'] = request()
(evidence / 'state.json').write_text(json.dumps(records, indent=2))
print(json.dumps(records['performance']), flush=True)
print(evidence, flush=True)
