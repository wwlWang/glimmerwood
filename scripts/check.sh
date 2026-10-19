#!/usr/bin/env sh
# Linux/macOS regression gate: LFS integrity, build (JUnit + release jar), headless GameTest server, totals.
# Usage: scripts/check.sh [--devtools] [extra gradle args]
set -eu
cd "$(dirname "$0")/.."
devtools=0
if [ "${1:-}" = "--devtools" ]; then devtools=1; shift; fi
command -v java >/dev/null || { echo "JDK 21 required" >&2; exit 2; }
git lfs version >/dev/null 2>&1 || { echo "git-lfs required (locked GemRender/Flywheel and models are LFS objects)" >&2; exit 2; }
git lfs fsck
# A pointer file instead of the real jar fails later with confusing class errors; catch it here.
for jar in libs/runtime/gemrender-0.1.17.jar libs/compile/flywheel-neoforge-1.21.1-1.0.6.jar; do
  head -c 4 "$jar" | od -An -c | grep -q 'P   K' || { echo "$jar is not a jar (run: git lfs pull)" >&2; exit 2; }
done
rm -f build/test-results/gametest.xml
./gradlew build runGameTestServer --console=plain "$@"
if [ "$devtools" = 1 ]; then
  python3 scripts/sync-dependencies.py
  ./gradlew compileDevtoolsJava --console=plain "$@"
fi
python3 scripts/export-spirit-fx.py --check
python3 scripts/test-summary.py
