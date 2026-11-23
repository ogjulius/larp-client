//! Which libraries Minecraft 1.8.9 + Forge need on a platform, and how to verify each one.

use crate::installer::{Endpoints, FORGE_VERSION};
use crate::{InstallError, json, maven, rules};
use serde_json::Value;
use std::collections::HashMap;

/// SHA-1s for libraries that neither Mojang's nor Forge's metadata gives a checksum for. The versions
/// are fixed for 1.8.9; each hash was checked against the `.sha1` files of every repository hosting it.
pub const PINNED_SHA1: &[(&str, &str)] = &[
    (
        "net.minecraft:launchwrapper",
        "111e7bea9c968cdb3d06ef4632bf7ff0824d0f36",
    ),
    ("org.ow2.asm:asm-all", "4333508b8dd8ee72aa4e39afa713b3a74579b773"),
    ("lzma:lzma", "521616dc7487b42bef0e803bd2fa3faf668101d7"),
    ("java3d:vecmath", "79846ba34cbd89e2422d74d53752f993dcc2ccaf"),
    ("net.sf.trove4j:trove4j", "42ccaf4761f0dfdfa805c9e340d99a755907e2dd"),
    (
        "org.scala-lang.plugins:scala-continuations-library_2.11",
        "0e517c53a7e9acd6b1668c5a35eccbaa3bab9aac",
    ),
    (
        "org.scala-lang.plugins:scala-continuations-plugin_2.11.1",
        "f361a3283452c57fa30c1ee69448995de23c60f7",
    ),
];

/// A library to download (or, for the Forge universal jar, to find already extracted).
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Library {
    pub key: String,
    /// Maven repository path, `/`-separated.
    pub path: String,
    /// `None` for the Forge universal jar, which comes out of the (verified) installer.
    pub url: Option<String>,
    /// Accepted SHA-1s (lower-case hex); the download must match one. Empty only when `url` is `None`.
    pub checksums: Vec<String>,
    pub natives: bool,
    /// Base URLs tried, in order, when `url` fails with an HTTP error.
    pub mirrors: Vec<String>,
}

/// The platform libraries are resolved for.
#[derive(Debug, Clone, Copy)]
pub struct Target<'a> {
    /// Mojang's OS name: `linux`, `windows` or `osx`.
    pub os: &'a str,
    pub os_version: &'a str,
    /// `"64"` or `"32"`.
    pub arch: &'a str,
}

/// Insertion-ordered map where replacing a key keeps its position (like Java's `LinkedHashMap`).
#[derive(Default)]
struct Ordered {
    items: Vec<Library>,
    index: HashMap<String, usize>,
}

impl Ordered {
    fn put(&mut self, library: Library) {
        match self.index.get(&library.key) {
            Some(&i) => self.items[i] = library,
            None => {
                self.index.insert(library.key.clone(), self.items.len());
                self.items.push(library);
            }
        }
    }

    fn contains(&self, key: &str) -> bool {
        self.index.contains_key(key)
    }
}

fn sha1_list(artifact: &Value) -> Vec<String> {
    json::str(artifact, "sha1")
        .map(|s| vec![s.to_ascii_lowercase()])
        .unwrap_or_default()
}

fn coordinate_path(name: &str) -> Result<String, InstallError> {
    maven::path(name).ok_or_else(|| InstallError::Metadata(format!("bad library coordinate {name:?}")))
}

/// Forge's libraries first (they win on conflicts), then vanilla's, filtered by `rules`, plus the
/// natives for `target`. Identical to the Java launcher (golden files in `testdata/launcher/golden`).
pub fn resolve(
    vanilla: &Value,
    forge_info: &Value,
    target: Target<'_>,
    endpoints: &Endpoints,
) -> Result<Vec<Library>, InstallError> {
    let mut result = Ordered::default();

    // Checksums vanilla knows, by repository path: Forge lists some of the same files without one.
    let vanilla_sha1: HashMap<&str, &str> = json::arr(vanilla, "libraries")
        .iter()
        .filter_map(|lib| json::obj(lib, "downloads").and_then(|d| json::obj(d, "artifact")))
        .filter_map(|a| Some((json::str(a, "path")?, json::str(a, "sha1")?)))
        .collect();

    let forge_coordinate = format!("net.minecraftforge:forge:{FORGE_VERSION}");
    for lib in json::arr(forge_info, "libraries") {
        let name = json::require(lib, "name", "Forge library")?;
        if lib.get("clientreq").and_then(Value::as_bool) == Some(false) {
            continue;
        }
        let path = coordinate_path(name)?;
        let key = maven::key(name);
        if name == forge_coordinate {
            result.put(Library {
                key,
                path,
                url: None,
                checksums: vec![],
                natives: false,
                mirrors: vec![],
            });
            continue;
        }
        let mut base = json::str(lib, "url")
            .unwrap_or(&endpoints.mojang_libraries)
            .replace("http://files.minecraftforge.net/maven/", &endpoints.forge_maven);
        if !base.ends_with('/') {
            base.push('/');
        }
        let checksums = if let Some(sha1) = vanilla_sha1.get(path.as_str()) {
            vec![sha1.to_ascii_lowercase()]
        } else {
            let listed: Vec<String> = json::arr(lib, "checksums")
                .iter()
                .filter_map(Value::as_str)
                .map(str::to_ascii_lowercase)
                .collect();
            if listed.is_empty() {
                PINNED_SHA1
                    .iter()
                    .filter(|(k, _)| *k == key)
                    .map(|(_, s)| (*s).to_owned())
                    .collect()
            } else {
                listed
            }
        };
        result.put(Library {
            url: Some(format!("{base}{path}")),
            key,
            path,
            checksums,
            natives: false,
            mirrors: vec![
                endpoints.forge_maven.clone(),
                endpoints.maven_central.clone(),
                endpoints.mojang_libraries.clone(),
            ],
        });
    }

    for lib in json::arr(vanilla, "libraries") {
        if !rules::allowed(json::arr(lib, "rules"), target.os, target.os_version) {
            continue;
        }
        let name = json::require(lib, "name", "Minecraft library")?;
        let key = maven::key(name);
        let downloads = json::obj(lib, "downloads");
        if let Some(artifact) = downloads.and_then(|d| json::obj(d, "artifact"))
            && !result.contains(&key)
        {
            result.put(Library {
                key: key.clone(),
                path: json::require(artifact, "path", name)?.to_owned(),
                url: json::str(artifact, "url").map(str::to_owned),
                checksums: sha1_list(artifact),
                natives: false,
                mirrors: vec![],
            });
        }
        let classifier = json::obj(lib, "natives").and_then(|n| json::str(n, target.os));
        if let Some(classifier) = classifier.map(|c| c.replace("${arch}", target.arch))
            && let Some(native) = downloads
                .and_then(|d| json::obj(d, "classifiers"))
                .and_then(|c| json::obj(c, &classifier))
        {
            result.put(Library {
                key: format!("{key}:{classifier}"),
                path: json::require(native, "path", name)?.to_owned(),
                url: json::str(native, "url").map(str::to_owned),
                checksums: sha1_list(native),
                natives: true,
                mirrors: vec![],
            });
        }
    }
    Ok(result.items)
}
