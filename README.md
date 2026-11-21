# LARP Client

A lightweight, Linux-first **PvP client for Minecraft 1.8.9**, built as a Forge mod with Mixins.
It focuses on the things PvP players actually use (clean HUD mods, zoom, freelook, keystrokes)
plus performance tweaks, without bloat, telemetry or launchers.

> LARP Client only provides **legitimate, cosmetic, informational and performance features**.
> It does not and will not contain combat cheats (killaura, reach, velocity, autoclicker, etc).
> Features that specific servers ask clients not to provide (e.g. Freelook on Hypixel) are
> disabled automatically on those servers.

## Features

| Category | Modules |
|---|---|
| **HUD** | FPS · CPS · Keystrokes · Armor Status · Potion Effects · Coordinates · Direction · Ping · Clock · Memory · Combo Counter · Reach Display · Server Address · Toggle Sprint |
| **Utility** | Zoom (smooth, scroll-adjustable) · Freelook · Fullbright · No Hurt Cam |
| **Performance** | Background FPS Limit · Particle Limiter · Entity Culling (distance) |

- **Mod menu**: press **Right Shift**. You can search, toggle, configure, and rebind any module.
- **HUD editor**: drag elements around with snapping, scroll to resize, right-click an element for its settings.
- **Profiles**: everything is saved to `.minecraft/config/larpclient/profile.json` (human-editable, atomic writes).

Default keys: `Right Shift` menu · `C` zoom · `Left Alt` freelook. You can change all of them in
*Options → Controls → LARP Client* or in the mod menu.

## Installing

1. Install **Minecraft Forge 1.8.9** (`11.15.1.2318` recommended) in your launcher.
2. Download `larpclient-<version>.jar` from Releases (or build it yourself, see below).
3. Drop it into `.minecraft/mods/` and start the Forge 1.8.9 profile.

For best performance on Linux, run the game on Java 8 with G1GC and 2–4 GB of RAM (e.g. `-Xmx3G -XX:+UseG1GC`).
LARP Client works with OptiFine 1.8.9 HD U M5 installed alongside it.

## Building from source

Requirements: JDK 17 (for Gradle) and JDK 8 (target/runtime). On Arch / CachyOS:

```sh
sudo pacman -S --needed jdk8-openjdk jdk17-openjdk
JAVA_HOME=/usr/lib/jvm/java-17-openjdk ./gradlew build
# → build/libs/larpclient-<version>.jar
```

Run the client in a dev environment with `./gradlew runGame`. See [DEVELOPMENT.md](DEVELOPMENT.md)
for the architecture, conventions and how to add a module.

## License

[MIT](LICENSE). Build tooling is derived from
[nea89o/Forge1.8.9Template](https://github.com/nea89o/Forge1.8.9Template) (public domain).
Minecraft is a trademark of Mojang AB; this project is not affiliated with Mojang or Microsoft.
