# Architecture

## 1. Current system (since 0.3.0; the Java launcher was removed)

```
┌──────────────── launcher (Rust, egui) ─────────────────────┐
│ larp-app ──► larp-install ──► larp-net (verified downloads)│
│ larp-cli     larp-launch      larp-core (offline accounts) │
│              └► LWJGL splice (classes from bundle/, Java)  │
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
| `larp-launch` | Java 8 discovery/download, argv builder, LWJGL class splice, process spawn (attached or detached), log4j XML → lines | `larp-core`, `quick-xml` |
| `larp-cli` / `larp-app` | CLI (same flags as today) and the GUI | `eframe`/`egui` with the OpenGL (glow) renderer, chosen in the Phase 3 trial (see REBUILD_PLAN.md, Phase 3) |

Rules:
- **Async only for I/O.** Downloads run on tokio. Hashing large files runs on `spawn_blocking`. The UI thread never blocks.
- **Bounded everything.** The download semaphore is sized like today's pool. The log pump uses a bounded channel and the console keeps a ring buffer (no unbounded memory growth during long sessions).
- **No panics across boundaries.** Library crates return `thiserror` errors. The binary turns them into user messages. `#![deny(clippy::unwrap_used)]` applies outside tests.
- **Accounts**: offline (non-premium) profiles only; Microsoft sign-in was removed (2026-11-23), so there are no secrets to store. `accounts.json` is still created 0600 atomically (fixes A1) because older versions kept refresh tokens in it, and the access token keeps its redaction newtype in logs.
- **Build integration**: Gradle still builds the mod. `./gradlew :bundle:rustBundle` writes the mod jar, the XWayland helper class, LWJGL's `LinuxMouse` patched by the Java `LwjglMousePatch` (same bytes as the Java launcher) and the pinned LWJGL SHA-1 to `launcher-rs/bundle/` (git-ignored). `larp-launch/build.rs` embeds them from there, or from `$LARP_BUNDLE_DIR`. Without a bundle the workspace still builds and tests, but the binary refuses to launch. CI builds Gradle first, then Cargo.

## 4. Mod (Java), unchanged structure

The mod keeps its architecture. Planned changes are fixes only (A5–A9). An optional later item is a
debounced background config save, so hotkey toggles persist without disk I/O on the render thread.
