//! Directory layout of a LARP installation (identical to the Java launcher's `GameFiles`).

use std::path::{Path, PathBuf};

pub const MC_VERSION: &str = "1.8.9";

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct GameFiles {
    pub root: PathBuf,
    pub libraries: PathBuf,
    pub assets: PathBuf,
    pub versions: PathBuf,
    pub runtimes: PathBuf,
    /// The game directory (`--gameDir`): worlds, mods, options, logs.
    pub instance: PathBuf,
    /// Patched copies of game libraries (the XWayland LWJGL fix).
    pub patches: PathBuf,
}

impl GameFiles {
    pub fn new(root: impl Into<PathBuf>) -> Self {
        let root = root.into();
        Self {
            libraries: root.join("libraries"),
            assets: root.join("assets"),
            versions: root.join("versions"),
            runtimes: root.join("runtime"),
            instance: root.join("instance"),
            patches: root.join("patches"),
            root,
        }
    }

    pub fn version_dir(&self, id: &str) -> PathBuf {
        self.versions.join(id)
    }

    pub fn natives_dir(&self, id: &str) -> PathBuf {
        self.version_dir(id).join("natives")
    }

    /// A library by its Maven repository path (always `/`-separated).
    pub fn library(&self, relative: &str) -> PathBuf {
        join_slash_path(&self.libraries, relative)
    }
}

/// Join a `/`-separated relative path onto `base` component by component (correct on Windows too).
pub fn join_slash_path(base: &Path, relative: &str) -> PathBuf {
    relative
        .split('/')
        .filter(|c| !c.is_empty())
        .fold(base.to_path_buf(), |p, c| p.join(c))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn layout_matches_java_launcher() {
        let f = GameFiles::new("/data/larpclient");
        assert_eq!(
            f.natives_dir("1.8.9"),
            PathBuf::from("/data/larpclient/versions/1.8.9/natives")
        );
        assert_eq!(
            f.library("a/b/c.jar"),
            PathBuf::from("/data/larpclient/libraries/a/b/c.jar")
        );
        assert_eq!(f.runtimes, PathBuf::from("/data/larpclient/runtime"));
    }
}
