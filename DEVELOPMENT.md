# Development Guide

The repository has three parts:

| Part | What | Language |
|---|---|---|
| `mod/` (Gradle) | Forge 1.8.9 mod, runs inside the game | Java 8 |
| `bundle/` (Gradle) | Build-time files the launcher embeds: the mod jar, the XWayland helper class and LWJGL's patched `LinuxMouse` | Java 17 (helper: 8) |
| `launcher-rs/` (Cargo) | The launcher: installs and starts the game, GUI and headless CLI | Rust |

```sh
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk
./gradlew build :bundle:rustBundle          # mod + tests, then the launcher bundle
./gradlew :mod:runGame                      # dev game with the mod (deobfuscated)
cd launcher-rs
cargo test --workspace                      # launcher tests (UI snapshots included)
cargo run --release -p larp-cli             # the launcher from source
cargo run --release -p larp-cli -- --launch --offline Dev   # headless end-to-end
scripts/e2e.sh                              # full end-to-end test (boots the game under Xvfb)
```

## Mod toolchain

| Piece | Version | Why |
|---|---|---|
| Minecraft | 1.8.9 | Target game version |
| Forge | 11.15.1.2318 | The de-facto standard 1.8.9 mod loader |
| Mappings | MCP `stable_22` | Human readable names (`thePlayer`, `getDebugFPS`, …) |
| Build | Gradle 8.8 + [Essential Loom](https://github.com/EssentialGG/architectury-loom) 0.10 | Modern Gradle support for legacy Forge |
| Mixin | 0.7.11 (runtime, shaded) / 0.8.5 (annotation processor) | Bytecode patches without coremods |
| Java | 17 runs Gradle, 8 compiles and runs the game | LaunchWrapper requires Java 8 |

`gradle.properties` points Gradle at `/usr/lib/jvm/java-{8,17}-openjdk` (Arch layout). Other
distros are detected automatically or downloaded through the Foojay toolchain resolver.

```sh
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk
./gradlew :mod:build      # compile, run unit tests, produce mod/build/libs/larpclient-<ver>.jar
./gradlew :mod:test       # unit tests only
./gradlew :mod:runGame    # launch Minecraft with the mod (offline dev account)
                       # (Loom's own runClient is broken on Gradle 8, see build.gradle.kts)
./gradlew :mod:genSources   # decompiled Minecraft sources for IDE navigation
```

To log in with a real account in dev runs, see [DevAuth](https://github.com/DJtheRedstoner/DevAuth)
(`-Ddevauth.enabled=true`).

IntelliJ IDEA: open the folder as a Gradle project, set the Gradle JVM to 17 and the project SDK to 8.

## Mod architecture

```
dev.larpclient
├── LarpClient            @Mod entry point: wires everything together in FMLInitializationEvent
├── Reference             compile-time constants (id, name, version)
├── core
│   ├── event             EventBus, Listener, Subscription, Cancellable + event types
│   ├── ForgeEventBridge  the ONLY Forge-bus subscriber; translates Forge events → LARP events
│   ├── ServerPolicy      per-server feature restrictions
│   ├── module            Module, Category, ModuleManager
│   ├── setting           Boolean/Number/Mode/Color/Key settings (self-serialising to JSON)
│   ├── config            ConfigManager: JSON profile load/save
│   ├── hud               HudModule, TextHudModule, HudRenderer
│   ├── gui               ModMenuScreen, HudEditorScreen, SettingWidget, Theme
│   │   └── menu          LarpMainMenu (replaces the title screen), MusicScreen, MenuBackground
│   ├── music             MusicPlayer + streaming MP3 (JLayer) / WAV decoders
│   └── util              CpsTracker, Animation, RenderUtil
├── modules               feature implementations (hud / utility / performance) + Modules registry
├── mixin                 bytecode patches (auto-discovered, see init/AutoDiscoveryMixinPlugin)
└── init                  mixin plugin
```

### Design principles

1. **Disabled features cost nothing.** Modules declare handlers with `on(EventType.class, handler)`
   in their constructor; handlers are attached to the bus only while the module is enabled.
2. **Own event bus.** `EventBus` dispatches by exact class with no reflection, and listener
   lists are copy-on-write, so posting an event is one map lookup plus an array walk. Hot events
   (`Render2DEvent`, `FovEvent`, `ClientTickEvent`) reuse a single instance, so there are no
   per-frame allocations.
3. **Mixins call into modules, not the other way round.** A mixin asks a static query on the
   module (`EntityCulling.shouldCull(...)`, `Freelook.isActive()`) which returns immediately when
   the module is off. Keep mixin bodies tiny.
4. **Minecraft-free core where possible.** Events, settings, modules, config, CPS tracking and
   animation logic don't touch game state, so they're covered by plain JUnit tests.
   Module constructors must never touch `Minecraft.getMinecraft()` state.
5. **Respect servers.** `Module#blockedReason()` lets a module refuse to enable on servers
   that disallow it; `ModuleManager#enforcePolicies()` runs on every world join.
6. **Robust persistence.** Profiles are written atomically. Unknown keys are ignored, missing
   modules get defaults, and corrupt files are moved to `profile.json.broken`.

### Event flow

```
Forge event ──► ForgeEventBridge ──► EventBus.post(LarpEvent) ──► enabled modules' handlers
Mixin hook  ──► Module static query (cheap early-out when disabled)
```

| LARP event | Source | Notes |
|---|---|---|
| `ClientTickEvent` | `TickEvent.ClientTickEvent` END | only while in a world |
| `Render2DEvent` | `RenderGameOverlayEvent.Post(ALL)` | HUD drawing |
| `KeyPressEvent` | `InputEvent.KeyInputEvent` | only with no GUI open |
| `MouseClickEvent` / `MouseScrollEvent` | `MouseEvent` | scroll is cancellable |
| `AttackEntityEvent` | Forge `AttackEntityEvent` | local player only |
| `FovEvent` | `EntityViewRenderEvent.FOVModifier` | mutable `fov` |
| `WorldLoadEvent` | `WorldEvent.Load` (client) | triggers policy enforcement |

### Mixins

| Mixin | Target | Purpose |
|---|---|---|
| `MixinMinecraft` | `getLimitFramerate` | Background FPS limit |
| `MixinEntity` | `setAngles` | Freelook: route mouse input to camera |
| `MixinEntityRenderer` | `orientCamera`, `hurtCameraEffect` | Freelook camera, No Hurt Cam |
| `MixinEffectRenderer` | `addEffect` | Particle limiter |
| `MixinRenderManager` | `shouldRender` | Distance-based entity culling |
| `MixinGuiScreen` | `drawBackground` | Starry sky instead of dirt outside a world |
| `MixinGuiSlot` | `drawContainerBackground`, `overlayBackground` | Translucent lists over the sky |

Mixins in `dev.larpclient.mixin` are discovered automatically, so you don't need to list them in
`mixins.larpclient.json`. Injectors default to `require = 1`, so a broken target fails loudly at
startup instead of silently doing nothing. Name injector methods `larp$<purpose>` to avoid clashes.

### Rendering & design system

LARP's menus share a small design system. They keep the vanilla look only for the in-game HUD,
which PvP players expect.

- `render/LarpFont` is anti-aliased Inter, rasterised into a glyph atlas at *size × GUI scale*,
  so text maps 1:1 to physical pixels at every GUI scale. `render/Ui` holds the type presets.
- `render/Shapes` draws rounded rects, outlines, circles, gradients, lines and media icons. Everything
  is built from plain `GL_TRIANGLES` with culling off, because polygon/fan modes proved unreliable
  in Minecraft's GL state.
- `render/StarrySky` is the procedural night sky behind every menu outside a world: a gradient, about 1,500
  seeded stars with twinkle and colour temperature turning slowly around an off-screen pole, a Milky
  Way band with glow, occasional shooting stars and two mountain ridges. `gui/menu/Backdrop` wraps it.
  `MixinGuiScreen`/`MixinGuiSlot` put vanilla menus (world list, options, servers) on the same sky.
- `gui/Widgets` provides the panel, toggle, slider, text input and close button.

**Screenshot tour.** `./gradlew :mod:runGame -PlarpTour [-PlarpTourScale=2] [-PlarpTourWidth=1280
-PlarpTourHeight=720] [-PlarpTourMouse=x,y]` renders every LARP screen offscreen at a fixed size
into `mod/run/screenshots/larp-tour-N.png`, then quits. It doesn't depend on your window manager.
Use it to review UI changes. The README images come from it.

### Fullscreen on Wayland compositors

LWJGL 2 implements fullscreen by recreating its window with `_NET_WM_STATE_FULLSCREEN` pre-set,
plus optional XRandR mode switches. Under XWayland, Hyprland ignores that pre-set state, so the game
renders at monitor size into a normal window (cropped picture, wrong mouse mapping). `platform/
CompositorFullscreen` keeps the window managed and sends the EWMH `_NET_WM_STATE` client message
through libX11 (via JNA, which ships with Minecraft). The compositor resizes the window and vanilla's
resize handling follows. `MixinMinecraft#toggleFullscreen` routes F11 there on Linux
(`-Dlarp.legacyFullscreen=true` disables it), and the launcher passes `-Dlarp.startFullscreen=true`
instead of `--fullscreen`. `-Dlarp.fullscreenProbe=true` toggles fullscreen by itself and logs sizes.
This was verified on Hyprland 0.57: the window reports fullscreen at 1920×1080 and the game follows, repeatedly.

### Mouse freeze under XWayland

LWJGL 2's `LinuxMouse` calls `XGetGeometry` (a blocking X round trip) up to three times per
pointer-motion event. Under XWayland each round trip can take milliseconds. With a 1000 Hz mouse,
the client thread falls seconds behind and the window looks frozen ("not responding"), windowed or
fullscreen. Because LWJGL is loaded by the system class loader, a Mixin can't fix this. Instead, the
build's `LwjglMousePatch` (`bundle/`) uses ASM to redirect those calls to `org.lwjgl.opengl.LarpWindowSizeCache`
(source set `bundle/src/gamepatch`, compiled for Java 8), which caches the size for 50 ms. The launcher
embeds both classes and verifies the LWJGL jar's SHA-1 before splicing them in. The game
then runs on a patched copy of the LWJGL jar in `<data>/patches/` (a full copy, because LWJGL's packages
are sealed). The copy's name includes the source jar's SHA-1, so it's rebuilt whenever LWJGL changes. Measured on Hyprland 0.57 with an RTX 2060 and a virtual 1000 Hz mouse: before the patch
the game froze indefinitely; after it, the menu holds 120 FPS while handling about 925 motion events/s.
`-Dlarp.inputProbe=true` logs FPS, worst frame time and cursor/camera movement once per second
(`=world` also opens the first singleplayer world after 5 s, to measure grabbed-mouse input).

### Performance

- `FastRender`: cancels `Minecraft#checkGLError` (a glGetError pipeline stall, several per
  frame), and lifts vanilla's 30 FPS menu cap to a configurable 120.
- Launcher: `-Xms = -Xmx`, G1 tuned for client allocation patterns (large young gen, 30 ms pause
  goal, string de-duplication, `DisableExplicitGC`). On Linux it sets `mesa_glthread=true` and
  `__GL_THREADED_OPTIMIZATIONS=1` (toggle in Settings) and disables driver-forced vsync.
- First run writes a fast `options.txt` (VBOs, no vsync, fast graphics, no clouds, fewer particles,
  no snooper or tutorial). It never overwrites an existing one.

### Usability testing in a virtual display

Robot-driven UI tests must not run on a live desktop: keyboard focus belongs to whatever window
the user is in, so synthetic input can end up somewhere else. Use an isolated X server instead:

```sh
Xvfb :77 -screen 0 1280x720x24 +extension GLX &
DISPLAY=:77 WAYLAND_DISPLAY= larp-client --launch --offline Dev
DISPLAY=:77 import -window root shot.png     # screenshot (ImageMagick)
```

With no window manager the game window is exactly the requested size, and input goes to the window
under the pointer.

### Music player

`MusicPlayer` streams on one daemon thread per track: HTTP (manual redirects) → `AudioDecoder`
(MP3 via shaded JLayer, WAV via Java Sound) → 16-bit PCM → volume scaling → `SourceDataLine`.
Pause blocks the worker on a monitor, and switching tracks cancels the old worker. The playlist is stored in
`config/larpclient/music.json`. OGG isn't supported yet; adding it means implementing
`AudioDecoder` with JOrbis, which already ships with Minecraft.

## Launcher architecture

Cargo workspace `launcher-rs/` (details in docs/rebuild/ARCHITECTURE.md):

```
larp-core     XDG paths, Settings (launcher.json), offline accounts (accounts.json, 0600), atomic writes
larp-net      verified downloads: SHA-1, bounded parallelism, retries with backoff
larp-install  vanilla JSON → client jar → Forge (universal jar + install profile) → libraries
              (rules, natives) → natives → assets → log4j config
larp-launch   Java 8 discovery / Mojang "jre-legacy" download, command line, LWJGL patch splice,
              process (attached, or detached with a crash check), log4j XML → lines
larp-app      the egui window: Home / Accounts / Settings / Console
larp-cli      the `larp-client` binary: GUI without arguments; --launch / --install-only / --export-icon
```

Files: `$XDG_DATA_HOME/larpclient` (`libraries/ assets/ versions/ runtime/ instance/ patches/`) and
`$XDG_CONFIG_HOME/larpclient` (`launcher.json`, `accounts.json`). The game directory is `instance/`.

Notes:
- **Log4Shell**: 1.8.9 ships log4j 2.0-beta9. The launcher always applies Mojang's patched logging
  config from the version JSON.
- **Bundled mod**: `./gradlew :bundle:rustBundle` writes the mod jar to `launcher-rs/bundle/`;
  `larp-launch/build.rs` embeds it, and it's installed into `instance/mods/` on every launch if the hash
  changed. A build without the bundle compiles and tests but refuses to launch.
- **Hyprland**: the window has a fixed size, so Hyprland floats it, and it is dragged through the
  compositor (Wayland-safe).

### Accounts

The launcher supports offline (non-premium) profiles only. Microsoft sign-in was removed on 2026-11-23.
An `accounts.json` entry of another type (a Microsoft account written by an older version) is loaded as
an offline profile with the same name, its tokens are dropped and the file is rewritten at once
(checked by the `legacy-microsoft-accounts*.json` golden files). A `microsoftClientId` key in
`launcher.json` is ignored and dropped on the next save.

## Adding a module

1. Create a class in `modules/<category>/`:

   ```java
   public final class MyHud extends TextHudModule {
       private final BooleanSetting fancy = setting(new BooleanSetting("Fancy", "Make it fancy", false));

       public MyHud() {
           super("My HUD", "What it does", 0.0, 0.25);   // name, description, default X/Y (0..1)
           on(ClientTickEvent.class, e -> { /* runs only while enabled */ });
       }

       @Override
       protected String getText() {
           return fancy.isOn() ? "✨ hi" : "hi";      // null hides the element
       }
   }
   ```

2. Register it in `modules/Modules.java`.
3. That's it: it appears in the mod menu, HUD editor and profile automatically.

Use `HudModule` directly for non-text elements (implement `getWidth/getHeight/render`).
For hold-to-use features, expose a static `KeyBinding` and add it to `Modules.keyBindings()`
so it shows up in vanilla Controls.

**Never add combat-advantage features** (aim assistance, reach/velocity modification, auto
clicking, ESP through walls, etc). PRs adding them will be rejected.

## Conventions

- Java 8 language level (no `var`, no records). 4-space indent, see `.editorconfig`.
- Setting names are persisted, so renaming one resets users' values. Module **names** derive the
  profile id (`"Armor Status"` → `armor_status`), so treat them as stable too.
- Keep `Reference.VERSION` in sync with `version` in `gradle.properties`.
- Commits: imperative mood, one logical change each (`Add potion effects HUD`).
- Run `./gradlew build` and `cargo test --workspace` (in `launcher-rs/`) before pushing. CI runs both,
  plus clippy and the end-to-end test.
- Compiler warnings are errors (`-Xlint:all -Werror`). The few suppressed categories are listed with
  reasons in each `build.gradle.kts`; don't add `@SuppressWarnings` without a comment saying why.
- Every bug fix comes with a regression test that fails without the fix.
- Gradle itself prints a handful of deprecation warnings. They come from the Shadow 8.1.1 and Essential
  Loom 0.10 plugins, not from our build scripts, and go away with plugin upgrades (Loom is pinned for
  legacy Forge support).

## Releasing

1. Bump `version` in `gradle.properties`, `Reference.VERSION` and `launcher-rs/Cargo.toml` (then
   `cargo update -w`).
2. Merge to `main` and wait for CI.
3. Tag `vX.Y.Z` and attach to a GitHub release: the `larp-client-linux-x86_64` artifact of that CI run
   (the binary the end-to-end test booted the game with), renamed `larp-client-X.Y.Z-linux-x86_64`, and
   `mod/build/libs/larpclient-X.Y.Z.jar`.
