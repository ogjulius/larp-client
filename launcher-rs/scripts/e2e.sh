#!/usr/bin/env bash
# End-to-end check for the Rust launcher (Phase 2 exit criterion, docs/rebuild/REBUILD_PLAN.md):
#   1. `larp-client --install-only` installs Minecraft 1.8.9 + Forge into a throwaway directory;
#   2. a second run over the finished install still succeeds;
#   3. `larp-client --launch --offline Dev` boots the game under Xvfb with the LARP mod loaded,
#      runs the mod's screenshot tour (-Dlarp.screenshotTour=true) and quits cleanly.
#
# Needs: a release build with the bundle embedded (./gradlew :launcher:rustBundle, then
# cargo build --release -p larp-cli), Java 8 in $JAVA_HOME or a standard location (else Mojang's runtime is downloaded), xvfb-run, network.
# Usage: scripts/e2e.sh [path/to/larp-client]   (never touches your real launcher data)
set -euo pipefail

here=$(cd "$(dirname "$0")/.." && pwd)
bin=${1:-$here/target/release/larp-client}
[[ -x $bin ]] || { echo "e2e: no launcher binary at $bin" >&2; exit 2; }
bin=$(cd "$(dirname "$bin")" && pwd)/$(basename "$bin")

work=$(mktemp -d "${TMPDIR:-/tmp}/larp-e2e.XXXXXX")
trap 'rm -rf "$work"' EXIT
export HOME=$work XDG_DATA_HOME=$work/data XDG_CONFIG_HOME=$work/config
game=$XDG_DATA_HOME/larpclient
mkdir -p "$XDG_CONFIG_HOME/larpclient"
cat >"$XDG_CONFIG_HOME/larpclient/launcher.json" <<'EOF'
{"memoryMb": 2048, "jvmArgs": "-XX:+UseG1GC -Dlarp.screenshotTour=true", "width": 854, "height": 480}
EOF

fail() {
    echo "e2e: FAIL: $*" >&2
    for log in "$work"/*.log; do
        echo "---- tail of $(basename "$log")" >&2
        tail -n 60 "$log" >&2
    done
    exit 1
}
expect() { grep -qF -- "$2" "$work/$1" || fail "$1 has no line containing: $2"; }

echo "e2e: install"
"$bin" --install-only >"$work/install.log" 2>&1 || fail "--install-only exited with $?"
expect install.log "[larp] Installed into $game"
[[ -s $game/instance/mods/larpclient.jar ]] || fail "mod jar was not installed"

echo "e2e: reinstall"
"$bin" --install-only >"$work/reinstall.log" 2>&1 || fail "second --install-only exited with $?"

echo "e2e: launch"
status=0
timeout 300 xvfb-run -a -s "-screen 0 1280x720x24" "$bin" --launch --offline Dev >"$work/launch.log" 2>&1 ||
    status=$?
[[ $status -eq 0 ]] || fail "--launch exited with $status"
expect launch.log "--username Dev"
expect launch.log "--accessToken 0"
expect launch.log "xwayland-mouse"
expect launch.log "Forge Mod Loader has successfully loaded 4 mods"
expect launch.log "[mcp, FML, Forge, larpclient]"
expect launch.log "Found mixins:"
expect launch.log "Stopping!"
for i in 0 1 2 3 4; do
    [[ -s $game/instance/screenshots/larp-tour-$i.png ]] || fail "screenshot tour did not write larp-tour-$i.png"
done
if grep -E "Exception|Caused by" "$work/launch.log" | grep -vE "twitch|Couldn't initialize" | grep -q .; then
    fail "the game log contains exceptions"
fi
echo "e2e: OK"
