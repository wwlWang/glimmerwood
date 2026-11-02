#!/usr/bin/env bash
# REL01: build the release jar twice from a clean tree, require identical sha256, and publish it with
# SHA256SUMS (both builds bypass the Gradle build cache, so each recompiles). The jar bundles Spirit Glow (orimod_shaderpack/spirit_glow.zip) with its MIT/Apache notices.
# Usage: scripts/build-release.sh [output-dir]   (default ../release next to the repo)
set -euo pipefail
cd "$(dirname "$0")/.."
out="${1:-$(cd .. && pwd)/release}"
[ -z "$(git status --porcelain --untracked-files=no)" ] || { echo "working tree has tracked changes; commit first" >&2; exit 1; }
commit=$(git rev-parse --short=10 HEAD)
version=$(sed -n 's/^mod_version=//p' gradle.properties)
build() { ./gradlew --no-daemon --no-build-cache --console=plain -q clean jar >/dev/null; ls build/libs/*.jar | grep -v -- '-sources\|-javadoc' | head -1; }
first=$(build); a=$(sha256sum "$first" | cut -d' ' -f1); cp "$first" /tmp/orimod-release-first.jar
second=$(build); b=$(sha256sum "$second" | cut -d' ' -f1)
[ "$a" = "$b" ] || { echo "not reproducible: $a vs $b" >&2; exit 1; }
for entry in orimod_shaderpack/spirit_glow.zip LICENSE THIRD_PARTY.md META-INF/neoforge.mods.toml; do
  unzip -l "$second" "$entry" >/dev/null || { echo "missing $entry in jar" >&2; exit 1; }
done
# R82: NeoForge must be an open 21.1 range (any 21.1.x >= the tested build), never an exact pin.
range=$(sed -n 's/^neo_version_range=//p' gradle.properties)
unzip -p "$second" META-INF/neoforge.mods.toml | grep -qF "versionRange=\"$range\"" || { echo "jar mods.toml lacks NeoForge range $range" >&2; exit 1; }
case "$range" in "["*","*")") ;; *) echo "NeoForge range $range is not open-ended" >&2; exit 1;; esac
unzip -p "$second" orimod_shaderpack/spirit_glow.zip > /tmp/orimod-sg.zip
for entry in LICENSE LICENSE-APACHE README.md shaders/shaders.properties; do
  unzip -l /tmp/orimod-sg.zip "$entry" >/dev/null || { echo "Spirit Glow zip lacks $entry" >&2; exit 1; }
done
mkdir -p "$out"
name="orimod-$version-$commit.jar"
cp "$second" "$out/$name"
# The locked GemRender (bundles Flywheel 1.0.6) must sit next to OriMod in mods/; ship it alongside.
cp libs/runtime/gemrender-0.1.17.jar "$out/gemrender-0.1.17.jar"
# GPL-3 text (shared by OriMod and GemRender) and the attribution / source pointers travel with both jars.
cp LICENSE "$out/LICENSE"; cp THIRD_PARTY.md "$out/THIRD_PARTY.md"
(cd "$out" && sha256sum "$name" > "$name.sha256" && sha256sum orimod-*.jar gemrender-0.1.17.jar > SHA256SUMS)
echo "$out/$name $b"
