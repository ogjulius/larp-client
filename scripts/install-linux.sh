#!/usr/bin/env bash
# Install the LARP Client launcher for the current user:
#   ~/.local/bin/larp-client                        (the launcher: a native binary, no Java needed to run it)
#   ~/.local/share/applications/larp-client.desktop (app menu entry)
#   ~/.local/share/icons/hicolor/256x256/apps/larp-client.png
# Usage: scripts/install-linux.sh [path/to/larp-client]
#   Without an argument it builds the launcher first, which needs JDK 17 and JDK 8 (to build the mod)
#   and a Rust toolchain.
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
data_home="${XDG_DATA_HOME:-$HOME/.local/share}"
bin_dir="$HOME/.local/bin"

binary="${1:-}"
if [[ -z "$binary" ]]; then
    command -v cargo >/dev/null || {
        echo "!! Building needs Rust (https://rustup.rs) — or pass a downloaded larp-client binary." >&2
        exit 1
    }
    echo ":: Building the mod and the files the launcher embeds"
    JAVA_HOME="${JAVA_HOME:-/usr/lib/jvm/java-17-openjdk}" "$repo_root/gradlew" -p "$repo_root" :bundle:rustBundle -q
    echo ":: Building the launcher"
    cargo build --release --locked -p larp-cli --manifest-path "$repo_root/launcher-rs/Cargo.toml"
    binary="$repo_root/launcher-rs/target/release/larp-client"
fi
[[ -x "$binary" ]] || { echo "!! Not an executable: $binary" >&2; exit 1; }

mkdir -p "$bin_dir" "$data_home/applications" "$data_home/icons/hicolor/256x256/apps"
install -m755 "$binary" "$bin_dir/larp-client.new"
mv -f "$bin_dir/larp-client.new" "$bin_dir/larp-client" # replaces the old Java wrapper script too
"$bin_dir/larp-client" --export-icon "$data_home/icons/hicolor/256x256/apps/larp-client.png"
# The Java launcher (0.2.0 and older) lived here; nothing uses it any more.
rm -rf "$data_home/larpclient/launcher"

cat > "$data_home/applications/larp-client.desktop" <<DESKTOP
[Desktop Entry]
Type=Application
Name=LARP Client
Comment=Minecraft 1.8.9 PvP client
Exec=$bin_dir/larp-client
Icon=larp-client
Terminal=false
Categories=Game;
StartupWMClass=larp-client
DESKTOP

command -v update-desktop-database >/dev/null && update-desktop-database "$data_home/applications" || true
echo ":: Installed. Start it from your app menu or run: larp-client"
[[ ":$PATH:" == *":$bin_dir:"* ]] || echo "   (add $bin_dir to your PATH to use the larp-client command)"
