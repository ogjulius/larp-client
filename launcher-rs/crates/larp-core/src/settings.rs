//! User preferences in `launcher.json`, the same file and format as the Java launcher.

use crate::safe_files;
use serde::{Deserialize, Serialize};
use std::io;
use std::path::{Path, PathBuf};

/// Keys older versions wrote that no longer mean anything; dropped on load rather than kept as unknown.
/// `microsoftClientId`: Microsoft sign-in was removed (offline profiles only).
const RETIRED_KEYS: &[&str] = &["microsoftClientId"];

/// Previous default, migrated automatically to [`DEFAULT_JVM_ARGS`].
const OLD_DEFAULT_JVM_ARGS: &str =
    "-XX:+UseG1GC -XX:+UnlockExperimentalVMOptions -XX:G1NewSizePercent=20 -XX:MaxGCPauseMillis=50";

/// G1 tuned for a game client on Java 8: short pauses, a large young generation, no `System.gc()`
/// stalls, string de-duplication.
pub const DEFAULT_JVM_ARGS: &str = "-XX:+UseG1GC -XX:+ParallelRefProcEnabled -XX:MaxGCPauseMillis=30 \
-XX:+UnlockExperimentalVMOptions -XX:G1NewSizePercent=30 -XX:G1MaxNewSizePercent=50 \
-XX:G1HeapRegionSize=8M -XX:G1ReservePercent=15 -XX:InitiatingHeapOccupancyPercent=20 \
-XX:+DisableExplicitGC -XX:+UseStringDeduplication -XX:+UseFastAccessorMethods";

pub const MIN_MEMORY_MB: i64 = 1024;
pub const MAX_MEMORY_MB: i64 = 16384;

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase", default)]
pub struct Settings {
    /// Heap size; out-of-range values are clamped on load (like the Java launcher), not rejected.
    pub memory_mb: i64,
    pub java_path: String,
    pub jvm_args: String,
    /// Mesa / NVIDIA threaded GL dispatch.
    pub threaded_gl: bool,
    pub width: u32,
    pub height: u32,
    pub fullscreen: bool,
    /// When false the launcher exits as soon as Minecraft has started.
    pub keep_launcher_open: bool,
    /// Keys this version doesn't know (e.g. written by a newer launcher) are kept, not dropped.
    #[serde(flatten)]
    pub unknown: serde_json::Map<String, serde_json::Value>,
}

impl Default for Settings {
    fn default() -> Self {
        Self {
            memory_mb: 3072,
            java_path: String::new(),
            jvm_args: DEFAULT_JVM_ARGS.to_owned(),
            threaded_gl: true,
            width: 1280,
            height: 720,
            fullscreen: false,
            keep_launcher_open: false,
            unknown: serde_json::Map::new(),
        }
    }
}

/// Settings plus where they came from.
#[derive(Debug, Clone)]
pub struct LoadedSettings {
    pub settings: Settings,
    pub file: PathBuf,
    /// Set if the file existed but couldn't be read: it was moved aside and defaults are in use.
    pub problem: Option<String>,
}

impl Settings {
    /// Load `file`, applying defaults for missing keys. A corrupt file is moved to
    /// `launcher.json.broken` and reported in [`LoadedSettings::problem`].
    pub fn load(file: &Path) -> LoadedSettings {
        let mut problem = None;
        let mut settings = match std::fs::read_to_string(file) {
            Ok(text) => match serde_json::from_str::<Option<Settings>>(&text) {
                Ok(Some(s)) => s,
                Ok(None) => {
                    problem = Some(safe_files::quarantine_after_load_failure(file, "file is empty"));
                    Settings::default()
                }
                Err(e) => {
                    let reason = if text.trim().is_empty() {
                        "file is empty".to_owned()
                    } else {
                        e.to_string()
                    };
                    problem = Some(safe_files::quarantine_after_load_failure(file, &reason));
                    Settings::default()
                }
            },
            Err(e) if e.kind() == io::ErrorKind::NotFound => Settings::default(),
            Err(e) => {
                problem = Some(format!(
                    "{} could not be read ({e}); defaults are in use.",
                    file.display()
                ));
                Settings::default()
            }
        };
        settings.normalize();
        LoadedSettings {
            settings,
            file: file.to_path_buf(),
            problem,
        }
    }

    fn normalize(&mut self) {
        self.memory_mb = self.memory_mb.clamp(MIN_MEMORY_MB, MAX_MEMORY_MB);
        for key in RETIRED_KEYS {
            self.unknown.remove(*key);
        }
        if self.jvm_args.trim() == OLD_DEFAULT_JVM_ARGS {
            DEFAULT_JVM_ARGS.clone_into(&mut self.jvm_args);
        }
    }

    pub fn save(&self, file: &Path) -> io::Result<()> {
        let json = serde_json::to_string_pretty(self).map_err(io::Error::other)?;
        safe_files::write_atomically(file, json.as_bytes(), false)
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::fs;

    fn golden(name: &str) -> PathBuf {
        Path::new(env!("CARGO_MANIFEST_DIR"))
            .join("../../../testdata/launcher/golden")
            .join(name)
    }

    #[test]
    fn reads_file_written_by_java_launcher() {
        let loaded = Settings::load(&golden("compat-launcher.json"));
        assert!(loaded.problem.is_none());
        let s = loaded.settings;
        assert_eq!(s.memory_mb, 4096);
        assert_eq!(s.java_path, "/usr/lib/jvm/java-8-openjdk/bin/java");
        assert_eq!(s.jvm_args, DEFAULT_JVM_ARGS);
        assert!(s.fullscreen && s.threaded_gl && !s.keep_launcher_open);
        assert!(s.unknown.is_empty());
    }

    #[test]
    fn writes_the_same_json_as_java_launcher() {
        let java = fs::read_to_string(golden("compat-launcher.json")).unwrap();
        let ours = serde_json::to_string_pretty(&Settings::load(&golden("compat-launcher.json")).settings).unwrap();
        assert_eq!(ours.trim(), java.trim());
    }

    #[test]
    fn missing_file_gives_defaults_without_problem() {
        let dir = tempfile::tempdir().unwrap();
        let loaded = Settings::load(&dir.path().join("launcher.json"));
        assert_eq!(loaded.settings, Settings::default());
        assert!(loaded.problem.is_none());
    }

    #[test]
    fn corrupt_file_is_quarantined_and_reported() {
        let dir = tempfile::tempdir().unwrap();
        let file = dir.path().join("launcher.json");
        fs::write(&file, "{\"memoryMb\": 4096,").unwrap();
        let loaded = Settings::load(&file);
        assert!(loaded.problem.is_some());
        assert_eq!(loaded.settings.memory_mb, 3072);
        assert_eq!(
            fs::read_to_string(dir.path().join("launcher.json.broken")).unwrap(),
            "{\"memoryMb\": 4096,"
        );
    }

    #[test]
    fn empty_and_null_files_count_as_corrupt() {
        for content in ["", "null"] {
            let dir = tempfile::tempdir().unwrap();
            let file = dir.path().join("launcher.json");
            fs::write(&file, content).unwrap();
            assert!(Settings::load(&file).problem.is_some(), "{content:?}");
        }
    }

    #[test]
    fn clamps_memory_migrates_old_jvm_defaults_and_keeps_unknown_keys() {
        let dir = tempfile::tempdir().unwrap();
        let file = dir.path().join("launcher.json");
        fs::write(
            &file,
            format!(r#"{{"memoryMb": 99999, "jvmArgs": "{OLD_DEFAULT_JVM_ARGS}", "futureKey": [1]}}"#),
        )
        .unwrap();
        let s = Settings::load(&file).settings;
        assert_eq!(s.memory_mb, MAX_MEMORY_MB);
        assert_eq!(s.jvm_args, DEFAULT_JVM_ARGS);
        s.save(&file).unwrap();
        assert!(fs::read_to_string(&file).unwrap().contains("\"futureKey\""));
    }

    #[test]
    fn microsoft_client_id_from_older_versions_is_dropped() {
        let dir = tempfile::tempdir().unwrap();
        let file = dir.path().join("launcher.json");
        fs::write(&file, r#"{"memoryMb": 4096, "microsoftClientId": "abc"}"#).unwrap();
        let s = Settings::load(&file).settings;
        assert!(s.unknown.is_empty());
        s.save(&file).unwrap();
        assert!(!fs::read_to_string(&file).unwrap().contains("microsoftClientId"));
    }
}
