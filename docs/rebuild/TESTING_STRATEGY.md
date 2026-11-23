# Testing Strategy

Principle: a feature is done when an automated test proves it works and CI blocks on that test.
Every bug fix lands with a regression test that failed before the fix.

## 1. Gates (CI, every push and PR)

| Stage | Java (mod + current launcher) | Rust launcher (Phase 2+) |
|---|---|---|
| Format | `.editorconfig` check (add Spotless) | `cargo fmt --check` |
| Compile | `-Xlint:all -Werror` (documented suppressions only) | `cargo check --all-targets` |
| Lint | — (evaluate Error Prone for the launcher) | `cargo clippy --all-targets -- -D warnings` |
| Unit | `./gradlew test` | `cargo test` |
| Integration | see §2 | see §2 |
| E2E | headless boot (§3) | headless boot (§3) |

Never weaken an assertion or disable a test to get green. A known failing test is marked with an issue link and a deadline.

## 2. Unit and integration coverage to add

| Area | Tests | Fixtures |
|---|---|---|
| `AccountStore` / `larp-core` accounts | file created 0600 from the first byte (A1). Corrupt file → quarantined, not overwritten (A2). Round-trip. | corrupt and truncated JSON |
| Settings | corrupt → quarantined with warning (A3). Unknown keys preserved/ignored. Java → Rust format compatibility. | real `launcher.json` samples |
| Microsoft auth | pending → slow_down → success; expired; declined; XSTS `XErr` mapping; 403 unapproved app; 404 no game | recorded JSON responses served by a fake `HttpClient` |
| Downloads | 404 no retry; 5xx retried with backoff; checksum mismatch → file not replaced; resume; atomic replace | local HTTP server (`wiremock` / JDK `HttpServer`) |
| Installer | 1.8.9 + Forge resolve to the exact expected library/native/asset set | recorded version JSON, Forge install profile, asset index |
| Command builder | substitution; JVM args with quotes/spaces (A4); LWJGL swap; token redaction | — |
| LWJGL patch | transform redirects only the size calls; unknown LWJGL refused; real 2.9.4 jar patched and verified by SHA | pinned jar hash |
| Music player | transport state machine; end-of-track vs user-skip race (A6); dead URL skipped (A8) | short MP3/WAV fixtures, local HTTP server |
| Config | hotkey toggle persisted (A5) | — |

Golden parity tests (Phase 2): the Java and Rust launchers produce identical file sets and argv for the same inputs.

## 3. End-to-end

`scripts/e2e.sh` (CI and local):
1. `Xvfb :77 -screen 0 1280x720x24 +extension GLX`
2. Launcher `--install-only` into a temp data dir (download cache restored by CI).
3. `--launch --offline Dev` with `-Dlarp.e2e=true`: the mod logs `initialised N modules`, opens and closes the mod menu, then exits through the normal shutdown path.
4. Assert: exit code 0, expected log lines, `profile.json` written and valid, no `Exception` in the log.

## 4. Performance

Repeatable workloads, results stored as CI artifacts and compared against the previous main build:

| Workload | Metrics | Tool |
|---|---|---|
| Launcher cold start → window shown; idle 10 min | wall time; RSS; RSS growth | `/usr/bin/time -v`, sampled `/proc/<pid>/status` |
| Install from empty cache vs warm cache | wall time; bytes; files verified | launcher timing logs |
| In-game: fixed seed world, scripted camera path, 120 s | avg FPS, 1 % low, frame-time p99, heap after GC | `InputProbe`-style probe extended to write CSV |
| XWayland mouse flood (virtual 1000 Hz mouse) | FPS, worst frame, events/s handled | existing `InputProbe` |
| Long session: 2 h in world with music streaming | heap and RSS slope, thread count | probe CSV |

The in-game benchmark needs a real GPU to give meaningful FPS numbers. In Xvfb (llvmpipe) it only detects relative regressions and leaks.
