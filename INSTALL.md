# Installing LARP Client

LARP Client is made for **Linux on Hyprland** (x86_64). The launcher is a single native program: no
Java needed to run it. Minecraft's Java 8 is found on your system or downloaded automatically.

## Option A: download (fastest)

1. Download `larp-client-0.3.0-linux-x86_64` from the [latest release](https://github.com/ogjulius/larp-client/releases/latest).
2. Make it executable and run it:
   ```sh
   chmod +x larp-client-0.3.0-linux-x86_64
   ./larp-client-0.3.0-linux-x86_64
   ```
   To add it to your app menu and get a `larp-client` command, run
   `./scripts/install-linux.sh path/to/larp-client-0.3.0-linux-x86_64` from a clone of this repository.
3. On **Accounts**, type a username and click **Add**, then click **Launch**.
   The first launch downloads Minecraft 1.8.9, Forge and Java 8 (about 300 MB) and then starts the game.

It needs glibc 2.35 or newer (any current Arch, Ubuntu 22.04+, Fedora 36+) and OpenGL.

## Option B: build from source (adds an app-menu entry)

```sh
sudo pacman -S --needed git jdk8-openjdk jdk17-openjdk rust   # JDKs build the mod; Rust builds the launcher
git clone https://github.com/ogjulius/larp-client
cd larp-client
./scripts/install-linux.sh
```

Start **LARP Client** from your app menu or run `larp-client`. Updating from 0.2.0 or older: the same
command replaces the old Java launcher, and your accounts and settings carry over.

## Using it

| Where | What |
|---|---|
| Main menu | Singleplayer / Multiplayer / **LARP Mods** / **Music** |
| In game | **Right Shift** opens the mod menu (or Esc → **LARP Mods**) |
| Mod menu | Toggle modules, open **Options**, **Edit HUD Layout** to drag elements |
| Keys | **C** zoom (scroll to adjust), **Left Alt** freelook. Change them in Controls. |
| Music | Paste a direct `.mp3`/`.wav` link or an MP3 radio stream, then click the track |

**Mod only:** drop `larpclient-0.3.0.jar` into `.minecraft/mods` of any Forge **1.8.9** install.

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
