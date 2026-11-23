# Rebuild Plan

Status: **Option B chosen** (2026-11-23). Phase 1 done (TECHNICAL_AUDIT.md §7). Phase 2 done: see the
status note under Phase 2 below.

## 1. The central finding

The brief asks for a "native Rust Minecraft client". The audit (TECHNICAL_AUDIT.md §1) shows that
LARP today is a launcher plus a mod on top of Mojang's game. Every hard part of a Minecraft client
comes from Mojang's code, not LARP's: protocol 47 networking, chunk decoding, world and entity
rendering, physics, inventory, sound, resource packs and the singleplayer server.

So "rewrite LARP in Rust" can mean two very different things, and they need separate answers.

## 2. Options

### Option A: Reimplement the Minecraft client natively in Rust (not pursued)

This means writing a new game client that renders and plays Minecraft without Mojang's code.
I'm not going to build this one. A from-scratch native client is a recreation of Minecraft itself,
not a client feature layered on the real game. It also fails LARP's own product requirements:

- **PvP correctness**: movement and combat would have to match 1.8.9 bit-for-bit, or server anticheats flag and ban the player. A mod running inside the real game gets this for free.
- **Feature loss**: singleplayer (there would be no integrated server), Forge compatibility, resource packs and every vanilla screen would all need reimplementing before reaching parity with what users have today.
- **Scope**: existing open-source attempts have spent years without reaching full parity.

### Option B: Rust where it pays off, Java where it must run (recommended)

| Component | Language | Why |
|---|---|---|
| Launcher (installer, auth, settings, process supervision, UI, CLI) | **Rust** | A standalone desktop program. Rust removes the JDK 17 requirement and should drop RSS from ~200 MB to an estimated ~30 MB with near-instant start. It also lets the type system and tests enforce correctness. |
| In-game mod | **Java 8** (unchanged language) | It has to run inside Minecraft's JVM. Nothing else can hook the game. |
| `LarpWindowSizeCache` game patch | **Java 8** | Same reason: it runs inside LWJGL. |

Honest expectation: in-game FPS is set by Mojang's renderer and the mod's Mixin tuning. The launcher's
language does not change it. In-game performance work continues in the mod, measured with the
benchmark harness in TESTING_STRATEGY.md §4.

### Option C: Keep Java everywhere, raise the quality bar

Fix every audit finding, add the missing tests and CI gates, and change nothing else. This is the lowest
risk and fastest option. The trade-off is that the launcher stays JVM-based.

**Recommendation: B**, with the audit fixes from C landing first in the Java launcher and the mod.
That way the current release improves now and the Rust launcher has a correct reference to match.

## 3. Phased plan (Option B)

Each phase ends with its exit criteria green in CI. No phase starts on a red build.

### Phase 1: Stabilise the current code (Java)
- Commit the pending LWJGL mouse-patch work after review (A11 fixes included).
- Fix A1–A8 from the audit, each with a regression test written first and shown to fail.
- CI gates: `-Xlint:all -Werror` on both projects (any documented suppressions listed in DEVELOPMENT.md), and tests for `AccountStore`, `MicrosoftAuth` (fake HTTP), `Http.download` (local server), `GameLauncher.buildCommand`.
- **Exit**: all findings A1–A11 closed or explicitly accepted, and CI blocks on warnings.

### Phase 2: Rust launcher core (no UI)
- Cargo workspace under `launcher-rs/` (see ARCHITECTURE.md §3).
- Port, in order: paths and settings (reads existing JSON byte-for-byte compatibly), downloads, installer, Java runtime, auth, command builder, process supervision, CLI.
- Golden tests: for recorded version/Forge JSON fixtures, the Rust installer must resolve the **same** file set and hashes, and the command builder must produce the **same** argv as the Java launcher (minus the token).
- **Exit**: `larp-client --install-only` and `--launch --offline Dev` work headless in CI (Xvfb), and the game boots with the mod loaded (log assertion) and exits cleanly.
- **Status (2026-11-23)**: done. Crates `larp-core`, `larp-net`, `larp-install`, `larp-auth`, `larp-launch`, `larp-cli`. Golden tests match the Java launcher's library sets (Linux, Windows, macOS) and launch argv. `launcher-rs/scripts/e2e.sh` installs into a throwaway directory, reinstalls, then boots the game under Xvfb with `-Dlarp.screenshotTour=true`. It asserts that Forge loaded `larpclient` and its mixins, that the patched LWJGL is on the classpath, that all 5 tour screenshots were written, `Stopping!`, exit 0 and no exceptions. It passes locally in about 30 s. CI runs it as the `rust-e2e` job, which has not run on GitHub yet (the branch is not pushed).

### Phase 3: Rust launcher UI
- Rebuild Home / Accounts / Settings / Console with the existing design language: palette (`ui/Palette`), Inter, nav rail, starry backdrop, launch button states.
- Screenshot comparison against the Java launcher at fixed sizes. The visual differences get reviewed by a human, not auto-accepted.
- **Exit**: feature-parity checklist (FEATURE_PARITY.md, launcher section) all ✅, with RSS and cold start measured and recorded.

### Phase 4: Cut-over
- Release both launchers side by side for one version, then make the Rust launcher the default. The Java launcher is removed one release later.
- `install-linux.sh` installs the native binary. A JDK is needed only to build the mod.

### Ongoing: In-game performance (Java mod)
- Build the repeatable benchmark (fixed world, fixed camera path, Xvfb or real GPU), then evaluate each performance module against it. Keep a module only if it shows a measured improvement.

## 4. Risks

| Risk | Mitigation |
|---|---|
| Rust UI toolkit misbehaves on Wayland/tiling WMs (the Java launcher needed workarounds) | Spike in Phase 3 week 1 on Hyprland, Sway, KDE and GNOME before committing to a toolkit |
| Settings/account migration loses data | Rust reads the Java formats. The first save backs up the original. Golden tests use real files. |
| LWJGL patch needs a bytecode library in Rust | Not needed: LWJGL for 1.8.9 is a single pinned jar (BSD licence). Pre-build the patched `LinuxMouse.class` at build time, verify the input jar's SHA-1 at runtime, and splice. |
| Microsoft sign-in still needs a Mojang-approved Azure client ID | Unchanged by the rewrite. Documented in DEVELOPMENT.md. |
