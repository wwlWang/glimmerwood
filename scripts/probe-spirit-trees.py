"""Read-only R100 placement preflight over an already loaded grid in the devtools world."""
import argparse
import json
from devtools_client import DevClient


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('build_id', help='New evidence directory name')
    parser.add_argument('--min-x', type=int, required=True)
    parser.add_argument('--max-x', type=int, required=True)
    parser.add_argument('--min-z', type=int, required=True)
    parser.add_argument('--max-z', type=int, required=True)
    parser.add_argument('--step', type=int, default=64)
    args = parser.parse_args()
    client = DevClient(args.build_id)
    result = client.action('spirit_tree_probe', minX=args.min_x, maxX=args.max_x,
                           minZ=args.min_z, maxZ=args.max_z, step=args.step)
    client.records['probe'] = result
    client.records['status'] = 'passed' if result['loadedChunksBefore'] == result['loadedChunksAfter'] else 'failed'
    client.save()
    print(json.dumps({k: result[k] for k in ('context', 'hearts', 'counts', 'eligibleCounts', 'byBiome',
                                            'loadedChunksBefore', 'loadedChunksAfter', 'elapsedMillis')}, indent=2))
    assert client.records['status'] == 'passed', 'Probe changed loaded chunk count'


if __name__ == '__main__':
    main()
