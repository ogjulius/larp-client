//! Crash-safe writes for config files.

use std::fs::{self, File, OpenOptions};
use std::io::{self, Write};
use std::path::{Path, PathBuf};

fn sibling(file: &Path, suffix: &str) -> PathBuf {
    let mut name = file.file_name().map(|n| n.to_os_string()).unwrap_or_default();
    name.push(suffix);
    file.with_file_name(name)
}

/// Create a new file that only its owner can read. On Unix the mode is set by `open(2)` itself, so
/// there is no moment at which other users can read it. Fails if the file already exists.
pub fn create_owner_only(path: &Path) -> io::Result<File> {
    let mut options = OpenOptions::new();
    options.write(true).create_new(true);
    #[cfg(unix)]
    {
        use std::os::unix::fs::OpenOptionsExt;
        options.mode(0o600);
    }
    options.open(path)
}

/// Replace `file` with `contents` so readers see the old or the new file, never a partial one.
/// The data is flushed to disk before the rename. With `owner_only` the file is never readable by others.
pub fn write_atomically(file: &Path, contents: &[u8], owner_only: bool) -> io::Result<()> {
    if let Some(parent) = file.parent().filter(|p| !p.as_os_str().is_empty()) {
        fs::create_dir_all(parent)?;
    }
    let tmp = sibling(file, ".tmp");
    // A leftover from a crash may have looser permissions: never reuse it.
    match fs::remove_file(&tmp) {
        Err(e) if e.kind() != io::ErrorKind::NotFound => return Err(e),
        _ => {}
    }
    let result = (|| {
        let mut out = if owner_only {
            create_owner_only(&tmp)?
        } else {
            OpenOptions::new().write(true).create_new(true).open(&tmp)?
        };
        out.write_all(contents)?;
        out.sync_all()?;
        drop(out);
        fs::rename(&tmp, file)
    })();
    if result.is_err() {
        let _ = fs::remove_file(&tmp); // best effort; the original error is what matters
    }
    result
}

/// Move an unreadable file to `<name>.broken` (replacing an older one) and return the new path.
pub fn quarantine(file: &Path) -> io::Result<PathBuf> {
    let broken = sibling(file, ".broken");
    fs::rename(file, &broken)?;
    Ok(broken)
}

/// After a failed load: quarantine the file and describe what happened, for the user.
/// Never fails; if the file can't even be moved, the message says so.
pub fn quarantine_after_load_failure(file: &Path, reason: &str) -> String {
    let name = file
        .file_name()
        .map(|n| n.to_string_lossy().into_owned())
        .unwrap_or_default();
    match quarantine(file) {
        Ok(broken) => format!(
            "{name} could not be read ({reason}). It was moved to {} and defaults are in use.",
            broken
                .file_name()
                .map(|n| n.to_string_lossy().into_owned())
                .unwrap_or_default()
        ),
        Err(e) => format!("{name} could not be read ({reason}) or moved aside ({e})."),
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[cfg(unix)]
    fn mode(p: &Path) -> u32 {
        use std::os::unix::fs::PermissionsExt;
        fs::metadata(p).unwrap().permissions().mode() & 0o777
    }

    #[cfg(unix)]
    #[test]
    fn owner_only_file_is_private_from_creation() {
        let dir = tempfile::tempdir().unwrap();
        let f = dir.path().join("secret.tmp");
        drop(create_owner_only(&f).unwrap());
        assert_eq!(fs::metadata(&f).unwrap().len(), 0);
        assert_eq!(mode(&f), 0o600);
    }

    #[cfg(unix)]
    #[test]
    fn stale_readable_temp_file_is_replaced() {
        use std::os::unix::fs::PermissionsExt;
        let dir = tempfile::tempdir().unwrap();
        let file = dir.path().join("accounts.json");
        let tmp = dir.path().join("accounts.json.tmp");
        fs::write(&tmp, "stale").unwrap();
        fs::set_permissions(&tmp, fs::Permissions::from_mode(0o644)).unwrap();
        write_atomically(&file, b"{}", true).unwrap();
        assert_eq!(fs::read_to_string(&file).unwrap(), "{}");
        assert_eq!(mode(&file), 0o600);
        assert!(!tmp.exists());
    }

    #[test]
    fn creates_parent_directories_and_replaces_content() {
        let dir = tempfile::tempdir().unwrap();
        let file = dir.path().join("a/b/launcher.json");
        write_atomically(&file, b"one", false).unwrap();
        write_atomically(&file, b"two", false).unwrap();
        assert_eq!(fs::read_to_string(&file).unwrap(), "two");
    }

    #[test]
    fn quarantine_keeps_content_and_explains() {
        let dir = tempfile::tempdir().unwrap();
        let file = dir.path().join("launcher.json");
        fs::write(&file, "{broken").unwrap();
        let msg = quarantine_after_load_failure(&file, "bad JSON");
        assert!(msg.contains("launcher.json.broken"), "{msg}");
        assert_eq!(
            fs::read_to_string(dir.path().join("launcher.json.broken")).unwrap(),
            "{broken"
        );
        assert!(!file.exists());
    }
}
