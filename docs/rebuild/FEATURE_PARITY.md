# Feature Parity Matrix

Every user-visible feature today, where it lives, its current status, and what happens to it under
the plan in REBUILD_PLAN.md (recommended path: **Rust launcher + Java in-game mod**).

Legend: ✅ works · ⚠️ works with known issue (see TECHNICAL_AUDIT.md) · 🦀 moves to Rust · ☕ stays Java (must run inside the game JVM)

## Launcher

| Feature | Today | Status | Target | Rust launcher (Phase 3) |
|---|---|---|---|---|
| One-click install: vanilla 1.8.9, Forge, libraries, natives, assets | `Installer` | ✅ | 🦀 | ✅ golden files + e2e |
| SHA-1 verified, parallel, resumable downloads | `Http.download`, `Installer` | ⚠️ A10 | 🦀 | ✅ |
| Java 8 discovery / Mojang `jre-legacy` download | `JavaRuntime` | ✅ | 🦀 | ✅ |
| Log4Shell-safe logging config | `Installer`, `GameLauncher` | ✅ | 🦀 | ✅ |
| Microsoft sign-in (device code → XBL → XSTS → MC) | `MicrosoftAuth` | ✅ (needs approved Azure ID) | 🦀 | ✅ tested against a mock server; live sign-in needs the approved Azure ID |
| Token refresh before launch | `HomePanel`, `Cli` | ✅ | 🦀 | ✅ |
| Offline profiles (vanilla-compatible UUID) | `Account.offline` | ✅ | 🦀 | ✅ |
| Account storage, owner-only file | `AccountStore` | ⚠️ A1, A2 | 🦀 (OS keyring when available, 0600 file fallback) | ⚠️ owner-only file, same as Java. The OS keyring waits for Phase 4: both launchers share `accounts.json` while they ship side by side |
| Settings: memory, resolution, fullscreen, Java path, JVM args, threaded GL, keep-open | `LauncherSettings`, `SettingsPanel` | ⚠️ A3, A4 | 🦀 | ✅ (A20 fixed in both launchers) |
| Tuned G1 flags, `-Xms = -Xmx`, Mesa/NVIDIA threaded GL env, vsync env | `GameLauncher` | ✅ | 🦀 | ✅ |
| Bundled mod install (hash-checked), stale copy cleanup | `GameLauncher.prepareInstance` | ✅ | 🦀 | ✅ |
| First-run fast `options.txt`, themed Forge splash | `GameLauncher` | ✅ | 🦀 | ✅ |
| XWayland mouse-freeze fix (patched LWJGL jar) | `LwjglMousePatch` + `gamepatch/` | ⚠️ A11, uncommitted | 🦀 patcher; ☕ `LarpWindowSizeCache` (runs in game) | ✅ |
| Live game console, log4j XML → text | `ConsolePanel`, `Log4jXmlFormatter` | ✅ | 🦀 | ✅ |
| Detached launch, launcher closes after start | `startDetached` | ✅ | 🦀 | ✅ e2e (`launch_hands_off_and_closes_the_launcher`) |
| Headless CLI: `--launch`, `--offline`, `--install-only`, `--export-icon` | `Cli` | ✅ | 🦀 (same flags) | ✅ same flags, plus `--page` |
| Home / Accounts / Settings / Console pages, nav rail, skins, starry backdrop | `ui/*` | ✅ | 🦀 (visual design preserved: palette, Inter font, layout) | ✅ awaiting human review of `screenshots/` (switches instead of Java's checkboxes) |
| Fixed-size window that floats on tiling WMs | `LauncherFrame` | ✅ | 🦀 | ✅ verified on Hyprland only; Sway, KDE and GNOME not yet tested |
| Linux desktop entry + `larp-client` command | `scripts/install-linux.sh` | ✅ | 🦀 (native binary, no JDK 17 needed) | ⏳ Phase 4 (`--export-icon` is ready; `install-linux.sh` still installs the Java launcher) |

## In-game (Forge mod), all ☕

| Category | Features | Status |
|---|---|---|
| Menus | Starry-sky main menu, vanilla menus on the sky, mod menu (Right Shift / pause button), HUD editor, music screen, onboarding | ✅ |
| HUD | FPS, CPS, Keystrokes, Armor Status, Potion Effects, Coordinates, Direction, Ping, Clock, Memory, Combo Counter, Reach Display, Server Address, Now Playing, Toggle Sprint | ✅ |
| Utility | Zoom (smooth, scroll), Freelook (blocked on servers that disallow it), Fullbright, No Hurt Cam | ✅ |
| Performance | Fast Render, Background FPS Limit, Particle Limiter, Entity Culling | ✅ |
| Display | Compositor fullscreen on Wayland/X11 (F11) | ✅ |
| HUD theme | Modern (rounded, Inter) / Classic, opacity, corners, accent | ✅ |
| Music | MP3/WAV URLs, MP3 radio, shuffle, autoplay, volume | ⚠️ A6, A7, A8; no OGG |
| Persistence | `profile.json`, `music.json`, atomic, quarantine on corruption | ⚠️ A5 |
| Server policy | Per-server feature blocking | ✅ |
| Keybinds | In vanilla Controls under "LARP Client" | ✅ |

## Must not change (user expectations)

- Vanilla-identical gameplay, movement and hit registration. This is a PvP client, and server anticheats flag deviations.
- Works as a plain Forge 1.8.9 mod in other launchers.
- Profile/setting names and module IDs (they are persisted, see DEVELOPMENT.md conventions).
- Data locations: `$XDG_DATA_HOME/larpclient`, `$XDG_CONFIG_HOME/larpclient`. The Rust launcher must read existing `launcher.json` / `accounts.json` without loss.
- No combat-advantage features.
