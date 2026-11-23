#![allow(clippy::unwrap_used, clippy::expect_used, clippy::panic)] // test code: failing loudly is the point

//! End to end through the real backend: press Launch in the GUI, install, boot Minecraft with the
//! LARP mod, watch it exit. Needs the network, Java 8 and a display (scripts/e2e.sh runs it under
//! Xvfb), so it is ignored by default:
//!
//! ```text
//! xvfb-run cargo test -p larp-app --test launch -- --ignored --test-threads=1
//! ```
//!
//! `LARP_E2E_ROOT` reuses an existing data directory instead of downloading everything again.

use egui_kittest::Harness;
use egui_kittest::kittest::Queryable;
use larp_app::backend::Backend;
use larp_app::model::{Launch, Model, Page};
use larp_app::ui::{App, WINDOW};
use larp_core::{Account, AccountStore, GameFiles, Settings};
use std::path::{Path, PathBuf};
use std::time::{Duration, Instant};

struct Env {
    _tmp: tempfile::TempDir,
    config: PathBuf,
    files: GameFiles,
}

/// Config with an offline "Dev" account; the game quits by itself after the mod's screenshot tour.
fn env(keep_open: bool) -> Env {
    let tmp = tempfile::tempdir().unwrap();
    let config = tmp.path().join("config");
    let root = std::env::var_os("LARP_E2E_ROOT").map_or_else(|| tmp.path().join("data"), PathBuf::from);
    let mut accounts = AccountStore::load(&config.join("accounts.json"));
    accounts.add(Account::offline("Dev"));
    accounts.save().unwrap();
    let settings = Settings {
        keep_launcher_open: keep_open,
        memory_mb: 2048,
        width: 854,
        height: 480,
        jvm_args: "-XX:+UseG1GC -Dlarp.screenshotTour=true".into(),
        ..Settings::default()
    };
    settings.save(&config.join("launcher.json")).unwrap();
    Env {
        _tmp: tmp,
        config,
        files: GameFiles::new(root),
    }
}

fn harness(env: &Env) -> Harness<'static, App> {
    let (files, config) = (env.files.clone(), env.config.clone());
    Harness::builder().with_size(WINDOW).build_eframe(move |cc| {
        larp_app::theme::install(&cc.egui_ctx);
        let backend = Backend::new(cc.egui_ctx.clone(), files.clone()).unwrap();
        let model = Model::new(files, &config, 8192);
        App::new(model, Box::new(backend), Page::Home)
    })
}

/// Step the UI in real time until `done`, or fail after `limit`.
fn step_until(harness: &mut Harness<'_, App>, limit: Duration, mut done: impl FnMut(&mut Harness<'_, App>) -> bool) {
    let start = Instant::now();
    while !done(harness) {
        assert!(
            start.elapsed() < limit,
            "timed out; console:\n{}",
            harness.state().model.console.text()
        );
        harness.step();
        std::thread::sleep(Duration::from_millis(50));
    }
}

fn console_has(harness: &Harness<'_, App>, text: &str) -> bool {
    harness.state().model.console.lines().any(|l| l.contains(text))
}

#[test]
#[ignore = "downloads Minecraft and starts it: run via scripts/e2e.sh"]
fn launch_with_the_launcher_open() {
    let env = env(true);
    let mut harness = harness(&env);
    harness.get_by_label("Launch").click();
    step_until(
        &mut harness,
        Duration::from_secs(600),
        |h| matches!(&h.state().model.launch, Launch::Busy { label, .. } if label == "Running"),
    );
    step_until(&mut harness, Duration::from_secs(300), |h| {
        h.state().model.launch == Launch::Idle
    });
    let model = &harness.state().model;
    assert_eq!(model.home_error, None, "console:\n{}", model.console.text());
    for expected in [
        "[launcher] Java: ",
        "xwayland-mouse",
        "Forge Mod Loader has successfully loaded 4 mods",
        "[mcp, FML, Forge, larpclient]",
        "Stopping!",
        "[launcher] Minecraft exited with code 0",
    ] {
        assert!(
            console_has(&harness, expected),
            "missing {expected:?}; console:\n{}",
            model.console.text()
        );
    }
    // The command line is shown (real tokens are redacted: larp-launch's token_is_redacted_for_display;
    // an offline profile's token is the literal "0", which is not a secret).
    assert!(
        console_has(&harness, "--username Dev"),
        "console:\n{}",
        model.console.text()
    );
    assert!(
        art_loaded(&harness, &env.files),
        "the panorama is available after the install"
    );
}

fn art_loaded(harness: &Harness<'_, App>, files: &GameFiles) -> bool {
    larp_app::art::client_jar(files).is_file() && harness.state().model.art.panorama.is_some()
}

#[test]
#[ignore = "downloads Minecraft and starts it: run via scripts/e2e.sh"]
fn launch_hands_off_and_closes_the_launcher() {
    let env = env(false);
    let mut harness = harness(&env);
    harness.get_by_label("Launch").click();
    // The game is started detached; once it survives the crash grace period the launcher closes.
    step_until(&mut harness, Duration::from_secs(600), |h| {
        h.output()
            .viewport_output
            .values()
            .any(|v| v.commands.contains(&eframe::egui::ViewportCommand::Close))
    });
    let log = env.files.instance.join("logs").join("launcher-output.log");
    assert!(log.is_file());
    assert!(console_has(
        &harness,
        &format!("[launcher] Game output: {}", log.display())
    ));
    // The game keeps running without the launcher and quits after its tour.
    let start = Instant::now();
    while !std::fs::read_to_string(&log).unwrap_or_default().contains("Stopping!") {
        assert!(start.elapsed() < Duration::from_secs(180), "game log:\n{}", tail(&log));
        std::thread::sleep(Duration::from_millis(500));
    }
    assert!(
        std::fs::read_to_string(&log)
            .unwrap()
            .contains("Forge Mod Loader has successfully loaded 4 mods")
    );
}

fn tail(path: &Path) -> String {
    let text = std::fs::read_to_string(path).unwrap_or_default();
    text.lines()
        .rev()
        .take(40)
        .collect::<Vec<_>>()
        .into_iter()
        .rev()
        .collect::<Vec<_>>()
        .join("\n")
}
