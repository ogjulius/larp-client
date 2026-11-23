# Technical Audit — LARP Client (Phase 0)

Audit date: 2026-11-23 · Commit `e25dfe2` + uncommitted LWJGL mouse-patch work.
Phase 1 status (branch `phase1-stabilise`): see §7.

## 1. What the product is

LARP Client is **not a Minecraft engine**. It is two Java programs layered on the real game:

| Part | Tech | Size | Role |
|---|---|---|---|
| `launcher/` | Java 17, Swing + FlatLaf, Gson, ASM | ~3.9k LOC, 32 files | Installs Minecraft 1.8.9 + Forge + Java 8, signs in (Microsoft device code / offline), builds the command line, starts and supervises the game process |
| `mod/` | Java 8, Forge 11.15.1.2318, Mixin 0.7, JLayer | ~6k LOC, 90 files | Runs inside Mojang's game: custom menus, HUD modules, zoom/freelook, music player, performance and Wayland fixes |

Mojang's client does everything else: networking (protocol 47), world and chunk decoding, rendering,
physics, entities, inventory, sound, resource packs and the integrated singleplayer server.
The repository contains **no** networking, chunk or world-rendering code of its own.

## 2. Baseline health

| Check | Result |
|---|---|
| `./gradlew build` (JDK 17 + 8) | ✅ passes |
| Unit tests | ✅ 41 tests, 0 failures, 0 skipped (mod: 9 classes, launcher: 2) |
| CI | GitHub Actions runs `./gradlew build` on push/PR |
| Compiler warnings | `-Xlint:unchecked,deprecation` on the mod; **not** treated as errors; the launcher has no lint flags |
| Static analysis / formatting check | none (only `.editorconfig`) |
| Coverage measurement | none |
| Integration / end-to-end tests | none automated; a manual Xvfb procedure exists in DEVELOPMENT.md |

## 3. Architecture assessment

Strengths, which any rebuild must keep:

- **Event bus** (`core/event/EventBus`): exact-class dispatch, copy-on-write lists, reused hot-event instances. Disabled modules cost nothing.
- **Game-free core**: modules, settings, config, CPS and animation are testable without Minecraft.
- **Mixins are tiny and fail loudly** (`require = 1`, auto-discovered).
- **Persistence is robust**: atomic writes, unknown keys ignored, corrupt profiles quarantined to `.broken`.
- **Server policy** (`ServerPolicy`, `Module#blockedReason`): features a server disallows are switched off on join.
- **Platform work with measured results**: compositor fullscreen on Wayland, and the LWJGL `XGetGeometry` patch (froze indefinitely before, 120 FPS at ~925 motion events/s after).
- **Installer**: SHA-1 verified, atomic, parallel and resumable downloads, plus Log4Shell mitigation.

Structural limitations:

- The launcher is a JVM app: ~150–250 MB RSS and a slow cold start for a program that mostly waits.
- The game itself is bound to Minecraft 1.8.9 / LWJGL 2 / Java 8. In-game performance is capped by Mojang's renderer. Only Mixin-level tuning is possible.
- Launcher UI code is custom-painted Swing with no tests.

## 4. Findings

Severity: **High**: data loss or security exposure. **Medium**: incorrect behaviour users can hit. **Low**: robustness or hygiene.

| # | Sev | Location | Finding | Failure scenario |
|---|---|---|---|---|
| A1 | High | `launcher/.../auth/AccountStore.java:75-77` | `accounts.json.tmp` is written with the default umask *before* `chmod 600`. | For a short window the file holding Microsoft refresh tokens is world-readable (umask 022 → 0644). Fix: create the file with `PosixFilePermissions.asFileAttribute` up front. |
| A2 | High | `launcher/.../auth/AccountStore.java:36` | Any parse error is swallowed and the store starts empty. The next `save()` overwrites the file. | A truncated or hand-edited `accounts.json` silently deletes every saved account and refresh token. The mod's `ConfigManager` already does the right thing (quarantine to `.broken` + log). |
| A3 | Medium | `launcher/.../config/LauncherSettings.java:45` | Same silent fallback for launcher settings. | Corrupt `launcher.json` resets memory/JVM/client-ID settings with no message. |
| A4 | Medium | `launcher/.../game/GameLauncher.java:114` | Custom JVM args are split on whitespace. | `-Dfoo="a b"` or a path with spaces becomes two broken arguments, and the game fails to start or misbehaves. |
| A5 | Medium | `mod/.../module/Module.java:75` via `ModuleManager.handleKey` | Toggling a module by hotkey does not persist. The profile is saved only when the mod menu or HUD editor closes, or by the shutdown hook. | The game crashes or is killed (no shutdown hook) and hotkey toggles since the last menu close are lost. |
| A6 | Medium | `mod/.../music/MusicPlayer.java:325` | `worker == this` is read outside the player lock before `next()`. | A track ends at the same moment the user picks another. The finished worker calls `next()` and overrides the user's choice. Fix: re-check inside `synchronized`. |
| A7 | Low | `mod/.../music/MusicPlayer.java:67,82,107` | `save()` (disk I/O) runs under the player lock on the render thread on every play/add/remove. | Frame-time spike on slow disks when changing tracks. |
| A8 | Low | `mod/.../music/MusicPlayer.java` (worker) | A failed track stops autoplay instead of skipping. | One dead radio URL ends the playlist. |
| A9 | Low | `LarpClient.java:102` | The shutdown hook serialises config while the client thread may still be mutating modules. | Rare inconsistent snapshot at exit (benign today: values are primitives). |
| A10 | Low | `launcher/.../util/Http.java:87` | Retries have no backoff or jitter. | All 3 attempts can hit the same transient outage within milliseconds. |
| A11 | Low | `launcher/.../game/LwjglMousePatch.java:67-93` | `.part` is not deleted when copying fails midway. The cache is keyed on mtime + `VERSION`, not content. | A stale `.part` stays behind (overwritten next time, so harmless). A replaced LWJGL jar with an older mtime would reuse the old patch. |
| A12 | Low (accepted) | `GameLauncher.java:138` | The Minecraft access token is passed in argv, which `/proc/<pid>/cmdline` exposes to other local users. | Inherent to 1.8.9's `--accessToken` contract, and the vanilla launcher does the same. Keep it documented. `redact()` already hides it in the UI console. |
| A13 | Low | build | No warnings-as-errors, no static analysis, launcher compiled without `-Xlint`. | Regressions in warnings go unnoticed. |
| A14 | Medium | `mod/.../module/ModuleManager.java` `enforcePolicies` | *Found in Phase 1.* A module switched off by a server policy (Freelook on Hypixel) was saved as disabled. | Freelook stays off on every server after visiting Hypixel once. |
| A15 | Medium | `mod/.../music/MusicPlayer.java` `save`/`load` | *Found in Phase 1.* `music.json` was written in place (not atomically), and an unreadable file was overwritten by the next save. | A crash mid-write or a corrupt file loses the playlist. |
| A16 | Medium | `launcher/.../auth/MicrosoftAuth.java` `refresh` | *Found in Phase 1.* A refresh response without `refresh_token` (allowed by OAuth) replaced the stored token with null. | The next refresh crashes with an NPE (`Map.of` rejects null) and the account becomes unusable. |
| A17 | Low | `launcher/.../auth/MicrosoftAuth.java` | *Found in Phase 1.* Non-JSON error pages, missing fields and an empty Xbox user-claims list escaped as `JsonSyntaxException` / `IndexOutOfBoundsException`. | Confusing errors instead of "Unexpected response from Xbox Live: missing …". |
| A18 | Low | `launcher/.../game/Installer.java` (parallel downloads) | *Found in Phase 1 (end-to-end run).* Workers count atomically but deliver progress out of order. | Progress jumps backwards (30 % → 14 % → 36 % → 10 %). |
| A19 | High | `launcher/.../game/Installer.java` `resolveLibraries`, `installForge` | *Found in Phase 2 (golden files).* The Forge installer and 18 of the 50 Linux libraries were downloaded with **no integrity check**. Forge's own `checksums` lists were ignored, and a Forge entry replaced vanilla's verified entry for the same file (`jopt-simple`). Five libraries had no checksum in any metadata. | A tampered or corrupted download from a mirror would be put on the game's classpath and run. |

Dev-only tools (`ScreenshotTour`, `FullscreenProbe`, the new `InputProbe`) ship in the release jar, gated by `-Dlarp.*` system properties. That is acceptable. They are inert unless the property is set.

## 5. Test coverage gaps

Covered: event bus, modules, settings, config load/save/quarantine, HUD layout math, CPS tracker, animation, music track validation, server-policy matching, Maven paths, library rules, offline UUIDs, log4j formatting, the LWJGL patch transform.

Not covered:
- `AccountStore` (permissions, corrupt-file handling): would have caught A1/A2.
- `MicrosoftAuth`: whole chain testable with a fake HTTP layer (pending → slow_down → success, XSTS error codes, 403/404 mapping).
- `Installer`: version JSON → library/native/asset resolution against recorded fixtures; checksum-mismatch and resume paths.
- `GameLauncher.buildCommand`: argument substitution, JVM-arg quoting (A4), LWJGL swap.
- `Http.download`: retry, 404 short-circuit, checksum mismatch, atomic replace (local HTTP server).
- `MusicPlayer` transport/state machine and decoders (fixture MP3/WAV files).
- End-to-end: launcher `--install-only` plus a headless game boot in Xvfb that asserts the mod initialised (log line) and exits cleanly.

## 6. Performance status

Measured in-repo: the XWayland mouse fix (above). Not measured: launcher RSS and cold-start time, in-game FPS / 1% lows before and after each performance module, music worker CPU. There is no repeatable benchmark workload yet. See TESTING_STRATEGY.md §4.

## 7. Phase 1 status

| # | Status | Commit | Regression test |
|---|---|---|---|
| A1 | Fixed | `581d418` | `SafeFilesTest.ownerOnlyFileIsCreatedPrivate`, `staleReadableTempFileIsReplaced` |
| A2 | Fixed | `581d418` | `AccountStoreTest.corruptFileIsQuarantinedNotOverwritten`, `emptyFileIsTreatedAsCorrupt`, `invalidEntriesAreDropped…` |
| A3 | Fixed | `581d418` | `LauncherSettingsTest.corruptFileIsQuarantinedAndReported` |
| A4 | Fixed | `490f843` | `GameLauncherTest.keepsQuotedArgumentsTogether`, `rejectsUnclosedQuote`, `commandUsesParsedJvmArgs…` |
| A5 | Fixed | `420cb54` | `ConfigManagerTest.hotkeyToggleIsPersisted`, `asyncSnapshotsNeverOverwriteNewerState` |
| A6 | Fixed | `b101a73` | `MusicPlayerTest.userChoiceWinsOverEndOfTrackAdvance` (checked by mutation: fails with the old check order) |
| A7 | Fixed | `b101a73` | `MusicPlayerTest.playlistChangesAreWrittenInBackground` |
| A8 | Fixed | `b101a73` | `MusicPlayerTest.autoplaySkipsDeadTrack`, `allDeadTracksEndInErrorWithoutLooping`, `userPickedDeadTrackShowsErrorAndStays` |
| A9 | **Accepted** | — | On a normal exit, `Minecraft#shutdown` finishes on the client thread, which is then blocked in `System.exit` while the hook runs, so nothing mutates modules concurrently. Only an external SIGTERM during play can race, and the serialised values are primitives (worst case: one toggle from that same instant is missing). |
| A10 | Fixed | `1d4130e` | `HttpTest.serverErrorsAreRetriedWithGrowingBackoff` (+ 6 more download tests) |
| A11 | Fixed | `e9f211d` | `LwjglMousePatchTest.rebuildsWhenSourceJarContentChanges`, `failedPatchLeavesNoPartialFile` |
| A12 | **Accepted** | — | Inherent to 1.8.9's `--accessToken` argument. Same as the vanilla launcher. UI output is redacted. |
| A13 | Fixed | `c699fd4` | CI: `./gradlew build` fails on any compiler warning |
| A14 | Fixed | `0378960` | `ModuleTest.policySuspensionIsTemporary`, `ConfigManagerTest.policySuspendedModuleIsSavedAsEnabled` |
| A15 | Fixed | `b101a73` | `MusicPlayerTest.corruptPlaylistIsQuarantined`, `saveIsAtomicAndLeavesNoTempFile` |
| A16 | Fixed | `998e94f` | `MicrosoftAuthTest.refreshWithoutNewRefreshTokenKeepsTheOldOne` |
| A17 | Fixed | `998e94f` | `MicrosoftAuthTest.malformedResponsesFailCleanly` (+ 9 more sign-in flow tests) |
| A18 | Fixed | `1014e24` | `ProgressTest.monotonicDropsStaleFractionsWithinAStep` |
| A19 | Fixed | Phase 2 branch | `InstallerTest.everyDownloadedLibraryHasAChecksum`, `forgeEntriesReuseVanillaChecksums`. Hashes pinned for the Forge installer and 7 libraries, each cross-checked against the `.sha1` files of every repository hosting it. |

End-to-end (manual, 2026-11-23): launcher jar from this branch, `--launch --offline Dev` under Xvfb with an isolated data and config dir. Install verified all files, the game ran on the v3 patched LWJGL jar, the mod logged `initialised 24 modules in 378 ms`, `profile.json` and `music.json` were written, and the log had no exceptions.

Carried into Phase 2 and now covered there: the `Installer` golden tests against recorded version/Forge JSON (`testdata/launcher/golden`, shared by the Java and Rust tests), and the scripted end-to-end test (`launcher-rs/scripts/e2e.sh`, CI job `rust-e2e`).
