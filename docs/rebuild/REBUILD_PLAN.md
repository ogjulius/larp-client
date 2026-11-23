# Rebuild Plan

Status: **Option B chosen** (2026-11-23). Phase 1 done (TECHNICAL_AUDIT.md §7). Phase 2 done. Phase 3
done (Hyprland only; offline profiles only). Phase 4 done: 0.3.0 ships only the Rust launcher. See the status notes under each phase below.

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
- **Toolkit trial (2026-11-23, Hyprland, RTX 2060)**, measured on a fixed 1080×680 window with the panning backdrop:

  | Candidate | Native Wayland, floats | PSS | CPU, 30 fps pan | First frame |
  |---|---|---|---|---|
  | egui (OpenGL) | yes | ~47–62 MB | ~3% | ~0.1 s |
  | iced (wgpu) | yes | 92–142 MB | not measured | 0.25–0.43 s |
  | iced (tiny-skia, CPU) | yes | 14–35 MB | 15–20% even at 6 fps (image drawing ~26 ns/px) | ~0.02 s |

  The user chose **egui**, keeping the pan. Slint was not tried: GPL or a royalty-free licence with attribution terms, a worse fit for this MIT project.
- **Status (2026-11-23)**: **exited**. Every exit item below is done or dropped. `larp-client` without arguments opens the egui launcher (crate `larp-app`). Covered by 23 unit tests, 9 UI tests driven through the accessibility tree with snapshots of every page and dialog, and 2 end-to-end tests that press Launch and boot the game with the mod (attached, and detached with the launcher closing). The end-to-end tests run in `scripts/e2e.sh`, which passes locally. Measured against the Java launcher on the same machine (3 runs each, Home page animating):

  | | Rust (egui) | Java (Swing) |
  |---|---|---|
  | Exec to window on screen | 0.50–0.64 s | 3.5–4.0 s |
  | Private memory | ~45 MB | 99–118 MB |
  | RSS | ~140 MB | 136–154 MB |
  | CPU | 3.8–6.0% | 4.0–7.1% |

  RSS is not the hoped-for ~30 MB. About 85 MB of it is the NVIDIA OpenGL driver mapped into the process, the price of the GPU pan. Ten minutes idle on Home: no memory growth, 2.3% CPU on average. (The end-of-run RSS was lower, but concurrent compilation was reclaiming pages at the time, so that figure is not a result.)

  Exit items (updated 2026-11-23):
  1. ✅ Screenshot review (`screenshots/`, Java vs Rust per page), done by Claude at the user's request. Home and Console match. Accepted differences: toggle switches instead of checkboxes, plain number fields instead of spinners, the JVM arguments field shows its start, and the decimal separator ("3.0 GB" vs the Java launcher following the system locale). `accounts.png` was regenerated after the offline-only change, both sides rendered under Xvfb with the real game art.
  2. ➖ Dropped: the user targets Hyprland only (2026-11-23), where the launcher is built, tested and used daily. Sway, KDE and GNOME are not tested.
  3. ✅ CI: all jobs green on GitHub, including the UI snapshots under Mesa lavapipe (recorded on NVIDIA) and the end-to-end game boot on Ubuntu 22.04.
  4. ➖ Dropped: Microsoft sign-in was removed on 2026-11-23 (offline profiles only).
  5. ➖ Dropped: no tokens are stored any more, so no OS keyring is needed.

  Found on the way: A20, the Java Settings page overflowing the window, now fixed with a regression test.

### Phase 4: Cut-over
- Release both launchers side by side for one version, then make the Rust launcher the default. The Java launcher is removed one release later.
- `install-linux.sh` installs the native binary. A JDK is needed only to build the mod.
- **Status (2026-11-23)**: done, in one step at the user's request. 0.2.0 shipped both launchers; 0.3.0 ships only the Rust one. The Java launcher is deleted. What the Rust build still needs from Java (the LWJGL `LinuxMouse` patch and the XWayland helper class) moved to the small Gradle module `bundle/`, which produces byte-identical files. `install-linux.sh` installs the native binary and removes the old Java launcher; accounts and settings carry over unchanged.

### Ongoing: In-game performance (Java mod)
- Build the repeatable benchmark (fixed world, fixed camera path, Xvfb or real GPU), then evaluate each performance module against it. Keep a module only if it shows a measured improvement.

## 4. Risks

| Risk | Mitigation |
|---|---|
| Rust UI toolkit misbehaves on Wayland/tiling WMs (the Java launcher needed workarounds) | Spike in Phase 3 week 1 on Hyprland, Sway, KDE and GNOME before committing to a toolkit |
| Settings/account migration loses data | Rust reads the Java formats. The first save backs up the original. Golden tests use real files. |
| LWJGL patch needs a bytecode library in Rust | Not needed: LWJGL for 1.8.9 is a single pinned jar (BSD licence). Pre-build the patched `LinuxMouse.class` at build time, verify the input jar's SHA-1 at runtime, and splice. |
