# Architecture

## 1. Current system

```
┌──────────────── launcher (Java 17 / Swing) ────────────────┐
│ ui/*  ──►  Installer ──► Http (verified downloads)         │
│            JavaRuntime     MicrosoftAuth ──► AccountStore  │
│            GameLauncher ──► LwjglMousePatch                │
└──────────────┬─────────────────────────────────────────────┘
               │ spawns: java8 -cp <libs, patched LWJGL> net.minecraft.launchwrapper.Launch
┌──────────────▼──────── game JVM (Java 8) ──────────────────┐
│ Minecraft 1.8.9 + Forge ──► LARP mod                       │
│   Forge events ──► ForgeEventBridge ──► EventBus ──► Modules│
│   Mixins ──► static module queries (cheap when disabled)   │
│   LarpWindowSizeCache (inside LWJGL, XWayland fix)         │
└────────────────────────────────────────────────────────────┘
```

See DEVELOPMENT.md for the mod internals (event table, mixin table, design system). They are not
repeated here.

## 2. Boundaries that do not move

- **Launcher ↔ game**: a process boundary. The contract is argv, environment, working directory and files (`instance/mods/larpclient.jar`, `options.txt`, `config/splash.properties`, patched LWJGL jar). Game stdout is log4j XML.
- **Mod ↔ Minecraft**: Forge events and Mixins only, Java 8 bytecode.
- **On-disk formats**: `launcher.json`, `accounts.json` (launcher), and `profile.json`, `music.json` (mod). Any new implementation must read the existing files.

## 3. Target launcher (Rust, Option B)

Cargo workspace `launcher-rs/`:

| Crate | Responsibility | Key dependencies |
|---|---|---|
| `larp-core` | XDG paths, `Settings`, `Account` types, JSON compatible with today's files, atomic writes with explicit permissions | `serde`, `serde_json`, `directories` |
| `larp-net` | `HttpClient` trait (real + fake), verified downloads, bounded parallelism, retry with backoff + jitter | `reqwest` (rustls), `sha1`, `tokio` |
| `larp-install` | Version manifest → libraries (rules, natives, Maven paths) → Forge 1.8.9 universal + install profile → assets → log4j config | `larp-net`, `zip` |
| `larp-auth` | Device-code → XBL → XSTS → Minecraft profile. Refresh. Typed errors that map to the existing user messages. | `larp-net` |
| `larp-launch` | Java 8 discovery/download, argv builder, LWJGL class splice, process spawn (attached or detached), log4j XML → lines | `larp-core`, `quick-xml` |
| `larp-cli` / `larp-app` | CLI (same flags as today) and the GUI | `eframe`/`egui` with the OpenGL (glow) renderer, chosen in the Phase 3 trial (see REBUILD_PLAN.md, Phase 3) |

Rules:
- **Async only for I/O.** Downloads run on tokio. Hashing large files runs on `spawn_blocking`. The UI thread never blocks.
- **Bounded everything.** The download semaphore is sized like today's pool. The log pump uses a bounded channel and the console keeps a ring buffer (no unbounded memory growth during long sessions).
- **No panics across boundaries.** Library crates return `thiserror` errors. The binary turns them into user messages. `#![deny(clippy::unwrap_used)]` applies outside tests.
- **Secrets**: refresh tokens go in the OS keyring when one is available, otherwise in a file created 0600 atomically (fixes A1). Tokens never appear in logs (a redaction newtype with no `Display`).
- **Build integration**: Gradle still builds the mod. `./gradlew :launcher:rustBundle` writes the mod jar, the XWayland helper class, LWJGL's `LinuxMouse` patched by the Java `LwjglMousePatch` (same bytes as the Java launcher) and the pinned LWJGL SHA-1 to `launcher-rs/bundle/` (git-ignored). `larp-launch/build.rs` embeds them from there, or from `$LARP_BUNDLE_DIR`. Without a bundle the workspace still builds and tests, but the binary refuses to launch. CI builds Gradle first, then Cargo.

## 4. Mod (Java), unchanged structure

The mod keeps its architecture. Planned changes are fixes only (A5–A9). An optional later item is a
debounced background config save, so hotkey toggles persist without disk I/O on the render thread.
