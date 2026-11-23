//! The game directory: the bundled mod, first-run defaults and the themed Forge splash.

use crate::LaunchError;
use larp_core::{GameFiles, safe_files};
use sha1::{Digest, Sha1};
use std::fs;

/// First run only: performance-oriented video settings and no tutorial/snooper noise.
const DEFAULT_OPTIONS: &[&str] = &[
    "useVbo:true",
    "fboEnable:true",
    "enableVsync:false",
    "maxFps:260",
    "fancyGraphics:false",
    "ao:0",
    "renderClouds:false",
    "particles:1",
    "entityShadows:false",
    "mipmapLevels:0",
    "renderDistance:8",
    "useNativeTransport:true",
    "snooperEnabled:false",
    "showInventoryAchievementHint:false",
    "pauseOnLostFocus:false",
    "guiScale:0",
];

const SPLASH: &[&str] = &[
    "enabled=true",
    "rotate=false",
    "showMemory=true",
    "background=0x141419",
    "font=0xEDEDF2",
    "barBorder=0x2C2C38",
    "barColor=0x8B5CF6",
    "barBackground=0x1C1C24",
    "memoryGoodColor=0x8B5CF6",
    "memoryWarnColor=0xE6E84A",
    "memoryLowColor=0xE5484D",
];

fn lines(items: &[&str]) -> String {
    let mut s = items.join("\n");
    s.push('\n');
    s
}

/// Install `mod_jar` as `mods/larpclient.jar` (only if it changed), remove other copies of the mod,
/// and write `options.txt` and the Forge splash config if they don't exist yet. Existing user files
/// are never overwritten.
pub fn prepare(files: &GameFiles, mod_jar: &[u8]) -> Result<(), LaunchError> {
    let mods = files.instance.join("mods");
    fs::create_dir_all(&mods).map_err(|e| LaunchError::io(&mods, e))?;
    let target = mods.join("larpclient.jar");
    let current = fs::read(&target).ok().map(|bytes| Sha1::digest(&bytes));
    if current != Some(Sha1::digest(mod_jar)) {
        safe_files::write_atomically(&target, mod_jar, false).map_err(|e| LaunchError::io(&target, e))?;
    }
    // Stale copies under other names (e.g. a manually installed version) would load twice.
    for entry in fs::read_dir(&mods).map_err(|e| LaunchError::io(&mods, e))? {
        let path = entry.map_err(|e| LaunchError::io(&mods, e))?.path();
        let name = path
            .file_name()
            .map(|n| n.to_string_lossy().into_owned())
            .unwrap_or_default();
        if name.starts_with("larpclient") && name.ends_with(".jar") && name != "larpclient.jar" {
            fs::remove_file(&path).map_err(|e| LaunchError::io(&path, e))?;
        }
    }
    write_if_missing(&files.instance.join("options.txt"), &lines(DEFAULT_OPTIONS))?;
    write_if_missing(&files.instance.join("config").join("splash.properties"), &lines(SPLASH))?;
    Ok(())
}

fn write_if_missing(path: &std::path::Path, content: &str) -> Result<(), LaunchError> {
    if path.exists() {
        return Ok(());
    }
    safe_files::write_atomically(path, content.as_bytes(), false).map_err(|e| LaunchError::io(path, e))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn installs_mod_and_defaults_without_touching_user_files() {
        let dir = tempfile::tempdir().unwrap();
        let files = GameFiles::new(dir.path());
        let mods = files.instance.join("mods");
        fs::create_dir_all(&mods).unwrap();
        fs::write(mods.join("larpclient-0.0.1.jar"), "old").unwrap();
        fs::write(mods.join("othermod.jar"), "keep").unwrap();
        fs::write(files.instance.join("options.txt"), "fov:90\n").unwrap();

        prepare(&files, b"mod v1").unwrap();

        assert_eq!(fs::read(mods.join("larpclient.jar")).unwrap(), b"mod v1");
        assert!(!mods.join("larpclient-0.0.1.jar").exists());
        assert!(mods.join("othermod.jar").exists());
        assert_eq!(
            fs::read_to_string(files.instance.join("options.txt")).unwrap(),
            "fov:90\n"
        );
        assert!(
            fs::read_to_string(files.instance.join("config/splash.properties"))
                .unwrap()
                .contains("barColor=0x8B5CF6")
        );
    }

    #[test]
    fn first_run_options_and_mod_updates() {
        let dir = tempfile::tempdir().unwrap();
        let files = GameFiles::new(dir.path());
        prepare(&files, b"mod v1").unwrap();
        let options = fs::read_to_string(files.instance.join("options.txt")).unwrap();
        assert!(options.starts_with("useVbo:true\n") && options.ends_with("guiScale:0\n"));
        prepare(&files, b"mod v2").unwrap();
        assert_eq!(fs::read(files.instance.join("mods/larpclient.jar")).unwrap(), b"mod v2");
    }
}
