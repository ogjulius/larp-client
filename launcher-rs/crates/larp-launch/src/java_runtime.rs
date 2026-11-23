//! Finds a Java 8 runtime for Minecraft 1.8.9: the configured path, then a system JDK/JRE 8, then
//! Mojang's own `jre-legacy` runtime, downloaded on demand.

use crate::LaunchError;
use larp_core::files::join_slash_path;
use larp_core::{GameFiles, Os};
use larp_install::Progress;
use larp_net::{Net, run_bounded};
use serde_json::Value;
use std::path::{Component, Path, PathBuf};
use std::sync::atomic::{AtomicUsize, Ordering};
use std::time::Duration;

pub const RUNTIME_INDEX: &str =
    "https://launchermeta.mojang.com/v1/products/java-runtime/2ec0cc96c44e5a76b9c8b7c39df7210883d12871/all.json";
const COMPONENT: &str = "jre-legacy";

#[derive(Debug, Clone)]
pub struct JavaRuntime {
    pub net: Net,
    pub files: GameFiles,
    pub os: Os,
    pub runtime_index: String,
}

impl JavaRuntime {
    pub fn new(net: Net, files: GameFiles) -> Self {
        Self {
            net,
            files,
            os: Os::current(),
            runtime_index: RUNTIME_INDEX.into(),
        }
    }

    /// The Java to run the game with.
    pub async fn resolve(&self, configured: &str, progress: &dyn Progress) -> Result<PathBuf, LaunchError> {
        let configured = configured.trim();
        if !configured.is_empty() {
            let path = PathBuf::from(configured);
            return if is_executable(&path) {
                Ok(path)
            } else {
                Err(LaunchError::Java(format!(
                    "Configured Java not found or not executable: {configured}"
                )))
            };
        }
        progress.update("Looking for Java 8", None);
        let env = |k: &str| std::env::var(k).ok();
        for candidate in system_candidates(self.os, &default_roots(self.os, &env), &env) {
            if is_java8(&candidate, Duration::from_secs(10)).await {
                return Ok(candidate);
            }
        }
        self.download_mojang_runtime(progress).await
    }

    /// Download (or verify) Mojang's `jre-legacy` runtime and return its Java executable.
    pub async fn download_mojang_runtime(&self, progress: &dyn Progress) -> Result<PathBuf, LaunchError> {
        let home = self.files.runtimes.join(COMPONENT);
        let java = home.join("bin").join(self.os.java_executable());
        progress.update("Downloading Java 8 runtime", None);
        let platform = self.os.runtime_platform(cfg!(target_pointer_width = "64"));
        let index: Value = parse(&self.net.get_text(&self.runtime_index).await?, "Java runtime index")?;
        let manifest_url = index
            .pointer(&format!("/{platform}/{COMPONENT}/0/manifest/url"))
            .and_then(Value::as_str)
            .ok_or_else(|| {
                LaunchError::Java(format!(
                    "No Java 8 runtime available for {platform}; install Java 8 and set it in Settings"
                ))
            })?;
        let manifest: Value = parse(&self.net.get_text(manifest_url).await?, "Java runtime manifest")?;
        let entries = manifest
            .get("files")
            .and_then(Value::as_object)
            .ok_or_else(|| LaunchError::Metadata("Java runtime manifest has no files".into()))?;

        let mut downloads = Vec::new();
        let mut links = Vec::new();
        for (name, info) in entries {
            if !is_safe_relative(name) {
                continue; // never write outside the runtime directory
            }
            let target = join_slash_path(&home, name);
            match info.get("type").and_then(Value::as_str) {
                Some("directory") => tokio::fs::create_dir_all(&target)
                    .await
                    .map_err(|e| LaunchError::io(&target, e))?,
                Some("link") => links.push((
                    target,
                    info.get("target")
                        .and_then(Value::as_str)
                        .unwrap_or_default()
                        .to_owned(),
                )),
                Some("file") => {
                    let raw = info
                        .pointer("/downloads/raw")
                        .ok_or_else(|| LaunchError::Metadata(format!("{name}: no download")))?;
                    let url = raw.get("url").and_then(Value::as_str).unwrap_or_default().to_owned();
                    let sha1 = raw.get("sha1").and_then(Value::as_str).unwrap_or_default().to_owned();
                    if url.is_empty() || sha1.is_empty() {
                        return Err(LaunchError::Metadata(format!("{name}: incomplete download info")));
                    }
                    downloads.push((
                        target,
                        url,
                        sha1,
                        info.get("executable").and_then(Value::as_bool) == Some(true),
                    ));
                }
                _ => {}
            }
        }
        let done = AtomicUsize::new(0);
        let total = downloads.len();
        run_bounded(downloads, 8, |(target, url, sha1, executable)| {
            let done = &done;
            async move {
                self.net.download(&url, &target, &[sha1]).await?;
                if executable {
                    make_executable(&target)?;
                }
                let n = done.fetch_add(1, Ordering::Relaxed) + 1;
                progress.update("Downloading Java 8 runtime", Some(n as f64 / total as f64));
                Ok::<(), LaunchError>(())
            }
        })
        .await?;
        for (link, target) in links {
            create_link(&home, &link, &target)?;
        }
        if !is_executable(&java) {
            return Err(LaunchError::Java("Downloaded Java runtime is incomplete".into()));
        }
        Ok(java)
    }
}

fn parse(text: &str, what: &str) -> Result<Value, LaunchError> {
    serde_json::from_str(text).map_err(|e| LaunchError::Metadata(format!("{what}: {e}")))
}

/// Relative, `/`-separated, and without `..`: stays inside the directory it is joined to.
fn is_safe_relative(name: &str) -> bool {
    !name.is_empty()
        && Path::new(name).components().all(|c| matches!(c, Component::Normal(_)))
        && !name.split('/').any(|c| c == "..")
}

/// Symlink `link` → `target` (relative to the link's directory) if it stays inside `home`.
fn create_link(home: &Path, link: &Path, target: &str) -> Result<(), LaunchError> {
    if link.symlink_metadata().is_ok() {
        return Ok(());
    }
    let parent = link.parent().unwrap_or(home);
    let mut resolved = parent.to_path_buf();
    for part in target.split('/') {
        match part {
            "" | "." => {}
            ".." => {
                resolved.pop();
            }
            p => resolved.push(p),
        }
    }
    if target.starts_with('/') || !resolved.starts_with(home) {
        return Ok(()); // a link pointing outside the runtime is ignored
    }
    std::fs::create_dir_all(parent).map_err(|e| LaunchError::io(parent, e))?;
    #[cfg(unix)]
    std::os::unix::fs::symlink(target, link).map_err(|e| LaunchError::io(link, e))?;
    Ok(())
}

fn make_executable(path: &Path) -> Result<(), LaunchError> {
    #[cfg(unix)]
    {
        use std::os::unix::fs::PermissionsExt;
        std::fs::set_permissions(path, std::fs::Permissions::from_mode(0o755)).map_err(|e| LaunchError::io(path, e))?;
    }
    #[cfg(not(unix))]
    let _ = path;
    Ok(())
}

fn is_executable(path: &Path) -> bool {
    #[cfg(unix)]
    {
        use std::os::unix::fs::PermissionsExt;
        path.metadata()
            .is_ok_and(|m| m.is_file() && m.permissions().mode() & 0o111 != 0)
    }
    #[cfg(not(unix))]
    path.is_file()
}

/// Where JDKs are commonly installed on `os`.
pub fn default_roots(os: Os, env: &dyn Fn(&str) -> Option<String>) -> Vec<PathBuf> {
    match os {
        Os::Linux => {
            let mut roots = vec![PathBuf::from("/usr/lib/jvm"), PathBuf::from("/usr/lib64/jvm")];
            roots.extend(env("HOME").map(|h| PathBuf::from(h).join(".sdkman/candidates/java")));
            roots
        }
        Os::Osx => vec![PathBuf::from("/Library/Java/JavaVirtualMachines")],
        Os::Windows => vec![
            PathBuf::from(r"C:\Program Files\Java"),
            PathBuf::from(r"C:\Program Files\Eclipse Adoptium"),
        ],
    }
}

/// `$JAVA_HOME/bin/java`, then likely Java 8 executables under `roots` (directories named like 8 / 1.8).
pub fn system_candidates(os: Os, roots: &[PathBuf], env: &dyn Fn(&str) -> Option<String>) -> Vec<PathBuf> {
    let exe = os.java_executable();
    let mut result: Vec<PathBuf> = env("JAVA_HOME")
        .map(|h| PathBuf::from(h).join("bin").join(exe))
        .into_iter()
        .collect();
    for root in roots {
        let Ok(entries) = std::fs::read_dir(root) else { continue };
        let mut dirs: Vec<PathBuf> = entries.flatten().map(|e| e.path()).collect();
        dirs.sort();
        for dir in dirs {
            if dir.file_name().is_some_and(|n| n.to_string_lossy().contains('8')) {
                result.push(dir.join("bin").join(exe));
                result.push(dir.join("Contents/Home/bin").join(exe));
                result.push(dir.join("jre/bin").join(exe));
            }
        }
    }
    result
}

/// Runs `java -version` (killed after `timeout`) and checks for a 1.8 version string.
pub async fn is_java8(java: &Path, timeout: Duration) -> bool {
    if !is_executable(java) {
        return false;
    }
    let mut command = tokio::process::Command::new(java);
    command
        .arg("-version")
        .stdin(std::process::Stdio::null())
        .kill_on_drop(true);
    match tokio::time::timeout(timeout, command.output()).await {
        Ok(Ok(out)) => {
            let text = format!(
                "{}{}",
                String::from_utf8_lossy(&out.stdout),
                String::from_utf8_lossy(&out.stderr)
            );
            text.contains("version \"1.8")
        }
        _ => false,
    }
}

#[cfg(test)]
#[cfg(unix)]
mod tests {
    use super::*;
    use std::os::unix::fs::PermissionsExt;

    fn fake_java(path: &Path, body: &str) {
        std::fs::create_dir_all(path.parent().unwrap()).unwrap();
        std::fs::write(path, format!("#!/bin/sh\n{body}\n")).unwrap();
        std::fs::set_permissions(path, std::fs::Permissions::from_mode(0o755)).unwrap();
    }

    #[tokio::test]
    async fn detects_java_8_by_version_output() {
        let dir = tempfile::tempdir().unwrap();
        let j8 = dir.path().join("j8/bin/java");
        fake_java(&j8, r#"echo 'openjdk version "1.8.0_402"' >&2"#);
        let j17 = dir.path().join("j17/bin/java");
        fake_java(&j17, r#"echo 'openjdk version "17.0.10"' >&2"#);
        let hangs = dir.path().join("hang/bin/java");
        fake_java(&hangs, "sleep 30");
        assert!(is_java8(&j8, Duration::from_secs(5)).await);
        assert!(!is_java8(&j17, Duration::from_secs(5)).await);
        assert!(
            !is_java8(&hangs, Duration::from_millis(200)).await,
            "a hanging java is given up on"
        );
        assert!(!is_java8(&dir.path().join("missing"), Duration::from_secs(1)).await);
    }

    #[test]
    fn candidates_prefer_java_home_then_java_8_named_dirs() {
        let dir = tempfile::tempdir().unwrap();
        for d in ["java-8-openjdk", "java-17-openjdk", "jdk1.8.0_202", "not-java"] {
            std::fs::create_dir_all(dir.path().join(d)).unwrap();
        }
        let env = |k: &str| (k == "JAVA_HOME").then(|| "/opt/jdk".to_owned());
        let c = system_candidates(Os::Linux, &[dir.path().to_path_buf()], &env);
        assert_eq!(c[0], PathBuf::from("/opt/jdk/bin/java"));
        assert!(c.contains(&dir.path().join("java-8-openjdk/bin/java")));
        assert!(c.contains(&dir.path().join("jdk1.8.0_202/jre/bin/java")));
        assert!(!c.iter().any(|p| p.starts_with(dir.path().join("java-17-openjdk"))));
    }

    #[tokio::test]
    async fn configured_java_must_exist() {
        let dir = tempfile::tempdir().unwrap();
        let rt = JavaRuntime::new(Net::new().unwrap(), GameFiles::new(dir.path()));
        let quiet = |_: &str, _: Option<f64>| {};
        assert!(rt.resolve("/no/such/java", &quiet).await.is_err());
        let java = dir.path().join("java");
        fake_java(&java, "true");
        assert_eq!(
            rt.resolve(&format!(" {} ", java.display()), &quiet).await.unwrap(),
            java
        );
    }

    #[test]
    fn manifest_paths_cannot_escape() {
        assert!(is_safe_relative("bin/java"));
        assert!(!is_safe_relative("../evil"));
        assert!(!is_safe_relative("/etc/passwd"));
        assert!(!is_safe_relative("bin/../../x"));
        assert!(!is_safe_relative(""));
    }
}
