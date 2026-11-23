# Installing LARP Client

## Option A: download (fastest)

1. Install **Java 17 or newer** (it runs the launcher; Minecraft's Java 8 is set up automatically).
   - Arch / CachyOS: `sudo pacman -S jdk17-openjdk`
   - Debian / Ubuntu: `sudo apt install openjdk-17-jre`
   - Fedora: `sudo dnf install java-17-openjdk`
   - Windows / macOS: install [Temurin 17](https://adoptium.net/temurin/releases/?version=17)
2. Download `larp-launcher-0.2.0.jar` from the [latest release](https://github.com/ogjulius/larp-client/releases/latest).
3. Run it:
   ```sh
   java -jar larp-launcher-0.2.0.jar
   ```
4. Click **Add account**, type a username, press **Enter**, then click **Launch**.
   The first launch downloads Minecraft 1.8.9, Forge and Java 8 (about 300 MB) and then starts the game.

### Preview: native Linux launcher (no Java needed)

The new launcher, written in Rust, starts in about half a second and uses less memory. It reads the
same accounts and settings as the Java launcher, so you can switch between them freely. It is a
preview: so far it has only been tested on Hyprland. Please report problems on other desktops.

```sh
chmod +x larp-client-0.2.0-linux-x86_64
./larp-client-0.2.0-linux-x86_64
```

It needs glibc 2.35 or newer (Ubuntu 22.04, Debian 12, Fedora 36, Arch and newer) and OpenGL.

## Option B: build from source (Linux, adds an app-menu entry)

```sh
sudo pacman -S --needed git jdk8-openjdk jdk17-openjdk   # or your distro's equivalents
git clone https://github.com/ogjulius/larp-client
cd larp-client
./scripts/install-linux.sh
```

Start **LARP Client** from your app menu or run `larp-client`.

## Using it

| Where | What |
|---|---|
| Main menu | Singleplayer / Multiplayer / **LARP Mods** / **Music** |
| In game | **Right Shift** opens the mod menu (or Esc → **LARP Mods**) |
| Mod menu | Toggle modules, open **Options**, **Edit HUD Layout** to drag elements |
| Keys | **C** zoom (scroll to adjust), **Left Alt** freelook. Change them in Controls. |
| Music | Paste a direct `.mp3`/`.wav` link or an MP3 radio stream, then click the track |

**Mod only:** drop `larpclient-0.2.0.jar` into `.minecraft/mods` of any Forge **1.8.9** install.

## Accounts

LARP uses **offline (non-premium) profiles** only: type any username (3–16 letters, numbers or
underscores) and play. They work in singleplayer and on offline-mode servers. Servers that require a
premium (Microsoft) account will refuse the connection. Microsoft sign-in is not supported.

## Troubleshooting

- **Game won't start:** open the launcher's **Console** page and read the last lines.
- **Game crashes at startup on Linux with `ArrayIndexOutOfBoundsException` in `LinuxDisplay`:** install
  `xrandr`, which Minecraft 1.8.9 needs to list display modes (`sudo pacman -S xorg-xrandr`,
  `sudo apt install x11-xserver-utils`, `sudo dnf install xrandr`).
- **Window is tiny on a tiling WM (Hyprland/Sway/i3):** press **F11** in game for fullscreen, or enable
  *Start in fullscreen* in launcher Settings.
- **Files:** game data lives in `~/.local/share/larpclient`, settings in `~/.config/larpclient`.
