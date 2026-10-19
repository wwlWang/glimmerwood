"""Recount docs/ACCEPTANCE_STATUS.md rows by status and check every summary line that quotes the totals.
Usage: python3 scripts/check-acceptance-counts.py   (exit 1 on a mismatch)"""
import collections, pathlib, re, sys
root = pathlib.Path(__file__).resolve().parent.parent
status = (root / 'docs/ACCEPTANCE_STATUS.md').read_text(encoding='utf-8')
rows = [l.split('|') for l in status.splitlines() if re.match(r'\| [A-Z]+[0-9]+ \|', l)]
ids = [r[1].strip() for r in rows]
plan = set(re.findall(r'^\| ([A-Z]+[0-9]+) \|', (root / 'docs/ACCEPTANCE.md').read_text(encoding='utf-8'), re.M))
count = collections.Counter(r[2].strip() for r in rows)
real = (count['通过'], count['部分验证'], count['未测'])
problems = []
if len(ids) != len(set(ids)): problems.append('duplicate ids')
if set(ids) != plan: problems.append(f'ids differ from ACCEPTANCE.md: {sorted(plan ^ set(ids))}')
if sum(real) != len(rows): problems.append(f'unknown status values: {count}')
quoted = {'docs/ACCEPTANCE_STATUS.md': r'当前计数为 (\d+) 通过、(\d+) 部分验证、(\d+) 未测',
          'docs/CLOUD_HANDOFF.md': r'目前 (\d+) 项通过、(\d+) 项部分验证、(\d+) 项未测'}
for path, pattern in quoted.items():
    m = re.search(pattern, (root / path).read_text(encoding='utf-8'))
    if not m: problems.append(f'{path}: totals line missing')
    elif tuple(map(int, m.groups())) != real: problems.append(f'{path}: quotes {m.groups()} but rows give {real}')
print(f'{len(rows)} items: {real[0]} pass / {real[1]} partial / {real[2]} untested; untested: {[r[1].strip() for r in rows if r[2].strip() == "未测"]}')
if problems: print('\n'.join(problems)); sys.exit(1)
