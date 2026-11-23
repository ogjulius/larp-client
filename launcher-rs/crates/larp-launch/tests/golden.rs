#![allow(clippy::unwrap_used, clippy::expect_used, clippy::panic)] // test code: failing loudly is the point

//! The Rust and Java launchers must build exactly the same command line (testdata/launcher/golden).

use larp_core::{Account, GameFiles, Settings};
use larp_install::InstalledGame;
use serde_json::Value;
use std::path::{Path, PathBuf};

fn load(name: &str) -> Value {
    let path = Path::new(env!("CARGO_MANIFEST_DIR"))
        .join("../../../testdata/launcher")
        .join(name);
    serde_json::from_str(&std::fs::read_to_string(path).unwrap()).unwrap()
}

#[test]
fn launch_command_linux() {
    let files = GameFiles::new("/data/larpclient");
    let vanilla = load("1.8.9.json");
    let profile = load("forge-install_profile.json");
    let mut classpath: Vec<PathBuf> = load("golden/libraries-linux.json")
        .as_array()
        .unwrap()
        .iter()
        .filter(|l| l["natives"] == false)
        .map(|l| files.library(l["path"].as_str().unwrap()))
        .collect();
    classpath.push(files.version_dir("1.8.9").join("1.8.9.jar"));
    let game = InstalledGame {
        version_id: profile["install"]["target"].as_str().unwrap().into(),
        main_class: profile["versionInfo"]["mainClass"].as_str().unwrap().into(),
        minecraft_arguments: profile["versionInfo"]["minecraftArguments"].as_str().unwrap().into(),
        asset_index: "1.8".into(),
        classpath: classpath.clone(),
        natives_dir: files.natives_dir("1.8.9"),
        logging_config: Some(files.assets.join("log_configs").join("client-1.7.xml")),
        logging_argument: vanilla["logging"]["client"]["argument"].as_str().map(str::to_owned),
    };
    let mut settings = Settings {
        fullscreen: true,
        ..Settings::default()
    };
    settings.jvm_args.push_str(" -Dlarp.test=\"a b\"");
    let account = Account::offline("Steve");

    let cmd = larp_launch::command::build(
        &game,
        &files,
        &account,
        &settings,
        Path::new("/usr/bin/java"),
        &classpath,
        ":",
    )
    .unwrap();

    let expected: Vec<String> = serde_json::from_value(load("golden/command-linux.json")).unwrap();
    for (i, (a, e)) in cmd.iter().zip(&expected).enumerate() {
        assert_eq!(a, e, "argument #{i}");
    }
    assert_eq!(cmd.len(), expected.len());
}

#[test]
fn token_is_redacted_for_display() {
    let mut account = Account::offline("Steve");
    account.access_token = larp_core::Secret::new("secret-token");
    let shown = larp_launch::command::redact(&["--accessToken".into(), "secret-token".into()], &account);
    assert_eq!(shown, "--accessToken <token>");
}
