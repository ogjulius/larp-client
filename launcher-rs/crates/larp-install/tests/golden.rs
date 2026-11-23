#![allow(clippy::unwrap_used, clippy::expect_used, clippy::panic)] // test code: failing loudly is the point

//! The Rust and Java launchers must resolve exactly the same libraries (testdata/launcher/golden).

use larp_install::Endpoints;
use larp_install::libraries::{Library, Target, resolve};
use serde_json::{Value, json};
use std::path::{Path, PathBuf};

fn testdata() -> PathBuf {
    Path::new(env!("CARGO_MANIFEST_DIR")).join("../../../testdata/launcher")
}

fn load(name: &str) -> Value {
    serde_json::from_str(&std::fs::read_to_string(testdata().join(name)).unwrap()).unwrap()
}

fn as_golden(libs: &[Library]) -> Value {
    Value::Array(
        libs.iter()
            .map(|l| {
                json!({
                    "key": l.key, "path": l.path, "url": l.url, "checksums": l.checksums,
                    "natives": l.natives, "mirrors": l.mirrors,
                })
            })
            .collect(),
    )
}

fn check(os: &str, os_version: &str) {
    let profile = load("forge-install_profile.json");
    let libs = resolve(
        &load("1.8.9.json"),
        &profile["versionInfo"],
        Target {
            os,
            os_version,
            arch: "64",
        },
        &Endpoints::default(),
    )
    .unwrap();
    let expected = load(&format!("golden/libraries-{os}.json"));
    let actual = as_golden(&libs);
    if actual != expected {
        let (a, e) = (actual.as_array().unwrap(), expected.as_array().unwrap());
        for (i, (x, y)) in a.iter().zip(e).enumerate() {
            assert_eq!(x, y, "{os}: first difference at library #{i}");
        }
        assert_eq!(a.len(), e.len(), "{os}: library count");
    }
}

#[test]
fn libraries_linux() {
    check("linux", "6.12.0");
}

#[test]
fn libraries_windows() {
    check("windows", "10.0");
}

#[test]
fn libraries_osx() {
    check("osx", "10.15.7");
}

#[test]
fn every_downloaded_library_is_verified() {
    let profile = load("forge-install_profile.json");
    for os in ["linux", "windows", "osx"] {
        let libs = resolve(
            &load("1.8.9.json"),
            &profile["versionInfo"],
            Target {
                os,
                os_version: "10.0",
                arch: "64",
            },
            &Endpoints::default(),
        )
        .unwrap();
        for l in libs.iter().filter(|l| l.url.is_some()) {
            assert!(
                !l.checksums.is_empty(),
                "{os}: {} would be downloaded unverified",
                l.key
            );
        }
    }
}
