//! Operating system detection and the launcher's data/config directories.

use std::path::{Path, PathBuf};

/// The operating systems the launcher supports, named as Mojang's metadata names them.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Os {
    Linux,
    Windows,
    Osx,
}

impl Os {
    /// The OS this binary was built for.
    pub const fn current() -> Self {
        if cfg!(target_os = "windows") {
            Self::Windows
        } else if cfg!(target_os = "macos") {
            Self::Osx
        } else {
            Self::Linux
        }
    }

    /// Mojang's name in library rules and natives maps: `linux`, `windows` or `osx`.
    pub const fn mojang_name(self) -> &'static str {
        match self {
            Self::Linux => "linux",
            Self::Windows => "windows",
            Self::Osx => "osx",
        }
    }

    pub const fn classpath_separator(self) -> &'static str {
        match self {
            Self::Windows => ";",
            Self::Linux | Self::Osx => ":",
        }
    }

    /// The Java executable inside a runtime's `bin/`. `javaw` on Windows: no console window.
    pub const fn java_executable(self) -> &'static str {
        match self {
            Self::Windows => "javaw.exe",
            Self::Linux | Self::Osx => "java",
        }
    }

    /// Mojang's name for this platform in the Java runtime manifest.
    pub const fn runtime_platform(self, x64: bool) -> &'static str {
        match (self, x64) {
            (Self::Windows, true) => "windows-x64",
            (Self::Windows, false) => "windows-x86",
            (Self::Osx, _) => "mac-os",
            (Self::Linux, true) => "linux",
            (Self::Linux, false) => "linux-i386",
        }
    }
}

/// `"64"` or `"32"`, as substituted for `${arch}` in natives classifiers.
pub const fn arch_bits() -> &'static str {
    if cfg!(target_pointer_width = "64") { "64" } else { "32" }
}

/// Where the launcher keeps game files (`data`) and its settings and accounts (`config`).
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Dirs {
    pub data: PathBuf,
    pub config: PathBuf,
}

impl Dirs {
    /// Directories for the current OS and process environment.
    pub fn from_env() -> Option<Self> {
        Self::resolve(Os::current(), |key| std::env::var(key).ok())
    }

    /// Same layout as the Java launcher: XDG on Linux (`$XDG_DATA_HOME/larpclient`,
    /// `$XDG_CONFIG_HOME/larpclient`), `%APPDATA%\larpclient` on Windows and
    /// `~/Library/Application Support/larpclient` on macOS, with `config/` inside the data dir there.
    /// Returns `None` if no home directory can be determined.
    pub fn resolve(os: Os, env: impl Fn(&str) -> Option<String>) -> Option<Self> {
        let home = env(if os == Os::Windows { "USERPROFILE" } else { "HOME" })
            .filter(|h| !h.is_empty())
            .map(PathBuf::from);
        // XDG values must be absolute; relative ones are ignored, as the spec requires.
        let xdg = |var: &str, fallback: &str| -> Option<PathBuf> {
            match env(var).filter(|v| Path::new(v).is_absolute()) {
                Some(v) => Some(PathBuf::from(v)),
                None => home.as_ref().map(|h| h.join(fallback)),
            }
        };
        match os {
            Os::Linux => Some(Self {
                data: xdg("XDG_DATA_HOME", ".local/share")?.join("larpclient"),
                config: xdg("XDG_CONFIG_HOME", ".config")?.join("larpclient"),
            }),
            Os::Windows => {
                let base = env("APPDATA").filter(|v| !v.is_empty()).map(PathBuf::from).or(home)?;
                let data = base.join("larpclient");
                Some(Self {
                    config: data.join("config"),
                    data,
                })
            }
            Os::Osx => {
                let data = home?.join("Library/Application Support/larpclient");
                Some(Self {
                    config: data.join("config"),
                    data,
                })
            }
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::collections::HashMap;

    fn env(pairs: &[(&str, &str)]) -> impl Fn(&str) -> Option<String> {
        let map: HashMap<String, String> = pairs.iter().map(|(k, v)| ((*k).to_owned(), (*v).to_owned())).collect();
        move |k| map.get(k).cloned()
    }

    #[test]
    fn linux_uses_xdg_with_home_fallback() {
        let d = Dirs::resolve(Os::Linux, env(&[("HOME", "/home/me")])).unwrap();
        assert_eq!(d.data, PathBuf::from("/home/me/.local/share/larpclient"));
        assert_eq!(d.config, PathBuf::from("/home/me/.config/larpclient"));

        let d = Dirs::resolve(
            Os::Linux,
            env(&[("HOME", "/home/me"), ("XDG_DATA_HOME", "/d"), ("XDG_CONFIG_HOME", "/c")]),
        )
        .unwrap();
        assert_eq!(d.data, PathBuf::from("/d/larpclient"));
        assert_eq!(d.config, PathBuf::from("/c/larpclient"));
    }

    #[test]
    fn relative_xdg_values_are_ignored() {
        let d = Dirs::resolve(Os::Linux, env(&[("HOME", "/home/me"), ("XDG_DATA_HOME", "relative")])).unwrap();
        assert_eq!(d.data, PathBuf::from("/home/me/.local/share/larpclient"));
    }

    #[test]
    fn windows_and_macos_keep_config_inside_data() {
        let d = Dirs::resolve(Os::Windows, env(&[("APPDATA", "C:\\Users\\me\\AppData\\Roaming")])).unwrap();
        assert_eq!(d.config, d.data.join("config"));
        let d = Dirs::resolve(Os::Osx, env(&[("HOME", "/Users/me")])).unwrap();
        assert_eq!(
            d.data,
            PathBuf::from("/Users/me/Library/Application Support/larpclient")
        );
    }

    #[test]
    fn no_home_means_no_dirs() {
        assert_eq!(Dirs::resolve(Os::Linux, env(&[])), None);
    }

    #[test]
    fn platform_names_match_mojang() {
        assert_eq!(Os::Linux.mojang_name(), "linux");
        assert_eq!(Os::Osx.runtime_platform(true), "mac-os");
        assert_eq!(Os::Windows.classpath_separator(), ";");
    }
}
