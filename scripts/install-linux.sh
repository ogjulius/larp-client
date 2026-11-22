#!/usr/bin/env bash
# Install the LARP Client launcher for the current user:
#   ~/.local/share/larpclient/launcher/larp-launcher.jar
#   ~/.local/bin/larp-client                       (command line entry point)
#   ~/.local/share/applications/larp-client.desktop (app menu entry)
# Usage: scripts/install-linux.sh [path/to/larp-launcher.jar]
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
data_home="${XDG_DATA_HOME:-$HOME/.local/share}"
install_dir="$data_home/larpclient/launcher"
bin_dir="$HOME/.local/bin"

find_java17() {
    # The launcher needs Java 17+; the game itself runs on Java 8 (found or downloaded automatically).
    local candidate
    for candidate in "${LARP_JAVA:-}" /usr/lib/jvm/java-{25,21,17}-openjdk/bin/java /usr/lib/jvm/java-{25,21,17}*/bin/java \
                     /usr/lib/jvm/default-runtime/bin/java "$(command -v java || true)"; do
        [[ -n "$candidate" && -x "$candidate" ]] || continue
        local major
        major="$("$candidate" -XshowSettings:properties -version 2>&1 | awk -F'= ' '/java.specification.version/ {print $2}')"
        if [[ "${major%%.*}" =~ ^[0-9]+$ && "${major%%.*}" -ge 17 ]]; then
            echo "$candidate"
            return 0
        fi
    done
    return 1
}

jar="${1:-}"
if [[ -z "$jar" ]]; then
    echo ":: Building launcher"
    JAVA_HOME="${JAVA_HOME:-/usr/lib/jvm/java-17-openjdk}" "$repo_root/gradlew" -p "$repo_root" :launcher:shadowJar -q
    jar="$(ls "$repo_root"/launcher/build/libs/larp-launcher-*.jar | grep -v thin | head -n1)"
fi

java_bin="$(find_java17)" || {
    echo "!! Java 17 or newer is required to run the launcher." >&2
    echo "   Arch/CachyOS: sudo pacman -S jdk17-openjdk   Debian/Ubuntu: sudo apt install openjdk-17-jre" >&2
    exit 1
}

mkdir -p "$install_dir" "$bin_dir" "$data_home/applications" "$data_home/icons/hicolor/256x256/apps"
cp "$jar" "$install_dir/larp-launcher.jar"
"$java_bin" -jar "$install_dir/larp-launcher.jar" --export-icon "$data_home/icons/hicolor/256x256/apps/larp-client.png"

cat > "$bin_dir/larp-client" <<WRAPPER
#!/usr/bin/env bash
exec "$java_bin" -Dawt.useSystemAAFontSettings=on -jar "$install_dir/larp-launcher.jar" "\$@"
WRAPPER
chmod +x "$bin_dir/larp-client"

cat > "$data_home/applications/larp-client.desktop" <<DESKTOP
[Desktop Entry]
Type=Application
Name=LARP Client
Comment=Minecraft 1.8.9 PvP client
Exec=$bin_dir/larp-client
Icon=larp-client
Terminal=false
Categories=Game;
StartupWMClass=dev-larpclient-launcher-Main
DESKTOP

command -v update-desktop-database >/dev/null && update-desktop-database "$data_home/applications" || true
echo ":: Installed. Start it from your app menu or run: larp-client"
[[ ":$PATH:" == *":$bin_dir:"* ]] || echo "   (add $bin_dir to your PATH to use the larp-client command)"
