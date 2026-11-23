#![allow(clippy::print_stdout)] // a build script talks to Cargo through stdout

//! Embeds the files produced by `./gradlew :bundle:rustBundle` (see bundle/build.gradle.kts).
//! Without them the launcher still builds (so `cargo test` works on its own), but launching the game
//! reports that this build has no bundled mod.

use std::path::PathBuf;
use std::{env, fs};

const FILES: [&str; 4] = [
    "larpclient.jar",
    "LarpWindowSizeCache.class",
    "LinuxMouse.class",
    "lwjgl.sha1",
];

fn main() {
    println!("cargo::rerun-if-env-changed=LARP_BUNDLE_DIR");
    let manifest = PathBuf::from(env::var("CARGO_MANIFEST_DIR").unwrap_or_default());
    let dir = env::var_os("LARP_BUNDLE_DIR").map_or_else(|| manifest.join("../../bundle"), PathBuf::from);
    println!("cargo::rerun-if-changed={}", dir.display());
    for f in FILES {
        println!("cargo::rerun-if-changed={}", dir.join(f).display());
    }
    let out = PathBuf::from(env::var("OUT_DIR").unwrap_or_default()).join("bundle.rs");
    let complete = FILES.iter().all(|f| dir.join(f).is_file());
    let code = if complete {
        let path = |f: &str| {
            format!(
                "{:?}",
                dir.join(f)
                    .canonicalize()
                    .unwrap_or_else(|_| dir.join(f))
                    .display()
                    .to_string()
            )
        };
        let sha1 = fs::read_to_string(dir.join("lwjgl.sha1"))
            .unwrap_or_default()
            .trim()
            .to_owned();
        format!(
            "pub static BUNDLE: Option<Bundle> = Some(Bundle {{\n    mod_jar: include_bytes!({}),\n    window_size_cache: include_bytes!({}),\n    linux_mouse: include_bytes!({}),\n    lwjgl_sha1: {:?},\n}});\n",
            path("larpclient.jar"),
            path("LarpWindowSizeCache.class"),
            path("LinuxMouse.class"),
            sha1
        )
    } else {
        println!(
            "cargo::warning=no bundle in {} (run ./gradlew :bundle:rustBundle): this build cannot launch the game",
            dir.display()
        );
        "pub static BUNDLE: Option<Bundle> = None;\n".to_owned()
    };
    if let Err(e) = fs::write(&out, code) {
        println!("cargo::error=cannot write {}: {e}", out.display());
    }
}
