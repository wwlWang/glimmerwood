"""Wait for startup resources before opening a saved development world."""
import argparse
import json
from pathlib import Path
import time
import urllib.error
import urllib.request

parser = argparse.ArgumentParser()
parser.add_argument('world')
args = parser.parse_args()
root = Path(__file__).resolve().parents[1]
deadline = time.monotonic() + 90
opened = False
while time.monotonic() < deadline:
    try:
        connection = json.loads((root / 'run-devtools/devtools/connection.json').read_text())
        headers = {'X-Ori-Devtools': connection['token'], 'Content-Type':'application/json'}
        request = urllib.request.Request(connection['url'] + '/state', headers=headers)
        with urllib.request.urlopen(request, timeout=3) as response:
            state = json.load(response)
        if state['screen'] == 'gameplay' and state['overlay'] == 'none':
            print('Development world ready', flush=True)
            break
        if not opened and state['overlay'] == 'none' and 'player' not in state:
            request = urllib.request.Request(connection['url']+'/action', headers=headers,
                data=json.dumps({'op':'open_world','name':args.world}).encode())
            with urllib.request.urlopen(request, timeout=55) as response:
                json.load(response)
            opened=True
    except (OSError, urllib.error.URLError, json.JSONDecodeError):
        pass
    time.sleep(.25)
else:
    raise TimeoutError('Development world did not become ready')
