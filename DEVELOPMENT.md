# Development Guide

The repository is a Gradle multi-project build:

| Project | What | Java |
|---|---|---|
| `mod/` | Forge 1.8.9 mod, runs inside the game | 8 |
| `launcher/` | Desktop launcher (Swing + FlatLaf). Installs and starts the game and bundles the mod jar | 17 |

```sh
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk
./gradlew build                 # everything + tests
./gradlew :mod:runGame          # dev game with the mod (deobfuscated)
./gradlew :launcher:run         # launcher from source
java -jar launcher/build/libs/larp-launcher-*.jar --launch --offline Dev   # headless end-to-end
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

Mixins in `dev.larpclient.mixin` are discovered automatically, so you don't need to list them in
`mixins.larpclient.json`. Injectors default to `require = 1`, so a broken target fails loudly at
startup instead of silently doing nothing. Name injector methods `larp$<purpose>` to avoid clashes.

### Music player

`MusicPlayer` streams on one daemon thread per track: HTTP (manual redirects) → `AudioDecoder`
(MP3 via shaded JLayer, WAV via Java Sound) → 16-bit PCM → volume scaling → `SourceDataLine`.
Pause blocks the worker on a monitor, and switching tracks cancels the old worker. The playlist is stored in
`config/larpclient/music.json`. OGG isn't supported yet; adding it means implementing
`AudioDecoder` with JOrbis, which already ships with Minecraft.

## Launcher architecture

```
dev.larpclient.launcher
├── Main / Cli            GUI entry point; headless --launch / --install-only / --export-icon
├── game
│   ├── Installer         vanilla JSON → client jar → Forge installer (universal jar + install_profile)
│   │                     → libraries (rules, natives, mirrors) → natives → assets → log4j config
│   ├── JavaRuntime       configured path → system Java 8 → Mojang "jre-legacy" download
│   ├── GameLauncher      instance prep (mod jar, themed Forge splash), command line, process + log pump
│   └── Log4jXmlFormatter turns Mojang's XML log output back into readable lines
├── auth                  Account, AccountStore (0600 file), MicrosoftAuth (device code → XBL → XSTS → MC)
├── config                LauncherSettings
└── ui                    LauncherFrame, Home/Accounts/Settings/Console pages, custom-painted components
```

Files: `$XDG_DATA_HOME/larpclient` (`libraries/ assets/ versions/ runtime/ instance/`) and
`$XDG_CONFIG_HOME/larpclient` (`launcher.json`, `accounts.json`). The game directory is `instance/`.

Notes:
- **Log4Shell**: 1.8.9 ships log4j 2.0-beta9. The launcher always applies Mojang's patched logging
  config from the version JSON.
- **Bundled mod**: `:launcher:processResources` copies `:mod:remapJar` into the jar as
  `larp/larpclient.jar.bin`. The suffix isn't `.jar` because Shadow would unpack it. It's
  installed into `instance/mods/` on every launch if the hash changed.
- **Tiling WMs**: the window has a fixed size so Hyprland/Sway/i3 float it, and it re-asserts
  its size after mapping because XWayland initial size hints are sometimes ignored.

### Microsoft sign-in

Microsoft login needs an Azure app registration whose client ID has been approved by Mojang for
the Minecraft services API. New apps must apply at
<https://aka.ms/mce-reviewappid>. Until then, `login_with_xbox` returns 403. To set it up:

1. Register an app at <https://portal.azure.com> → *App registrations* (personal Microsoft accounts,
   "Allow public client flows" = yes, no redirect URI needed for device code).
2. Request Minecraft API access for that client ID.
3. Paste the client ID in *Launcher → Settings → Microsoft sign-in*.

Offline profiles work without any of this, for singleplayer and offline-mode servers.

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
- Run `./gradlew build` before pushing. CI runs the same command.

## Releasing

1. Bump `version` in `gradle.properties` and `Reference.VERSION`.
2. `./gradlew clean build`
3. Tag `vX.Y.Z` and attach `launcher/build/libs/larp-launcher-X.Y.Z.jar` (and optionally
   `mod/build/libs/larpclient-X.Y.Z.jar`) to a GitHub release.
