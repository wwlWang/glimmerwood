"""Encode FrameRecorder images with their measured timing, preserving real playback speed."""
import argparse
import json
from pathlib import Path
import subprocess
import shutil

parser = argparse.ArgumentParser()
parser.add_argument('manifest', type=Path)
parser.add_argument('--output', type=Path)
args = parser.parse_args()
manifest = args.manifest.resolve()
recording = json.loads(manifest.read_text(encoding='utf-8'))
if 'video' in recording:
    source = manifest.parent / recording['video']
    assert source.is_file()
    if args.output:
        assert not args.output.exists()
        shutil.copy2(source, args.output)
    print(args.output or source)
    raise SystemExit(0)
frames = recording['frames']
assert len(frames) >= 2
lines = ['ffconcat version 1.0']
for index, frame in enumerate(frames):
    name = frame['file']
    assert Path(name).name == name and "'" not in name
    assert (manifest.parent / name).is_file()
    end = frames[index + 1]['seconds'] if index + 1 < len(frames) else recording['durationSeconds']
    duration = end - frame['seconds']
    assert duration > 0
    lines.extend([f"file '{name}'", f'duration {duration:.9f}'])
lines.append(f"file '{frames[-1]['file']}'")
timeline = manifest.with_suffix('.ffconcat')
timeline.write_text('\n'.join(lines) + '\n', encoding='utf-8')
output = args.output.resolve() if args.output else manifest.parent.with_suffix('.mp4')
subprocess.run(['ffmpeg', '-hide_banner', '-loglevel', 'warning', '-n', '-safe', '0', '-f', 'concat',
                '-i', str(timeline), '-fps_mode', 'vfr', '-c:v', 'libx264', '-crf', '18',
                '-pix_fmt', 'yuv420p', '-movflags', '+faststart', str(output)], check=True)
print(output)
