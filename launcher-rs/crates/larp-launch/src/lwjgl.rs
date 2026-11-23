//! The XWayland mouse-freeze fix: run the game on a copy of LWJGL 2 whose `LinuxMouse` caches window
//! size queries (see DEVELOPMENT.md, "Mouse freeze under XWayland"). The patched class comes
//! prebuilt from the Gradle build, so it is byte-identical to what the Java launcher produces, and
//! it is only spliced into the exact LWJGL jar it was made from.

use crate::LaunchError;
use sha1::{Digest, Sha1};
use std::fs::{self, File};
use std::io::{Read, Write};
use std::path::{Path, PathBuf};

pub const TARGET: &str = "org/lwjgl/opengl/LinuxMouse.class";
pub const HELPER: &str = "org/lwjgl/opengl/LarpWindowSizeCache.class";
/// Shared with the Java launcher's `LwjglMousePatch.VERSION`, so both reuse the same patched jar.
pub const PATCH_VERSION: u32 = 3;

/// The prebuilt patch.
#[derive(Debug, Clone, Copy)]
pub struct Patch<'a> {
    pub source_sha1: &'a str,
    pub linux_mouse: &'a [u8],
    pub helper: &'a [u8],
}

/// Index of the LWJGL 2 jar on the classpath, if any.
pub fn find(classpath: &[PathBuf]) -> Option<usize> {
    classpath.iter().position(|p| {
        p.file_name()
            .map(|n| n.to_string_lossy())
            .is_some_and(|n| n.starts_with("lwjgl-2.") && n.ends_with(".jar"))
    })
}

fn sha1_of(path: &Path) -> Result<String, LaunchError> {
    let mut file = File::open(path).map_err(|e| LaunchError::io(path, e))?;
    let mut hasher = Sha1::new();
    let mut buf = vec![0u8; 64 * 1024];
    loop {
        let n = file.read(&mut buf).map_err(|e| LaunchError::io(path, e))?;
        if n == 0 {
            break;
        }
        hasher.update(&buf[..n]);
    }
    Ok(hex::encode(hasher.finalize()))
}

/// Deletes the partial output on any early return.
struct Partial(PathBuf);

impl Drop for Partial {
    fn drop(&mut self) {
        let _ = fs::remove_file(&self.0); // usually already renamed away
    }
}

/// Build (or reuse) the patched copy of `lwjgl_jar` in `dir` and return its path. Blocking.
pub fn prepare(lwjgl_jar: &Path, dir: &Path, patch: &Patch<'_>) -> Result<PathBuf, LaunchError> {
    let sha1 = sha1_of(lwjgl_jar)?;
    if !sha1.eq_ignore_ascii_case(patch.source_sha1) {
        return Err(LaunchError::UnknownLwjgl { actual: sha1 });
    }
    let base = lwjgl_jar
        .file_stem()
        .map(|s| s.to_string_lossy().into_owned())
        .unwrap_or_default();
    let prefix = format!("{base}-xwayland-mouse-");
    let out = dir.join(format!("{prefix}v{PATCH_VERSION}-{}.jar", &sha1[..12]));
    if out.is_file() {
        return Ok(out);
    }
    fs::create_dir_all(dir).map_err(|e| LaunchError::io(dir, e))?;
    let partial = Partial(out.with_extension("jar.part"));
    write_patched(lwjgl_jar, &partial.0, patch)?;
    fs::rename(&partial.0, &out).map_err(|e| LaunchError::io(&out, e))?;
    delete_stale(dir, &prefix, &out);
    Ok(out)
}

fn write_patched(source: &Path, target: &Path, patch: &Patch<'_>) -> Result<(), LaunchError> {
    let zip_err = |path: &Path| {
        let path = path.to_path_buf();
        move |source| LaunchError::Zip { path, source }
    };
    let input = File::open(source).map_err(|e| LaunchError::io(source, e))?;
    let mut archive = zip::ZipArchive::new(input).map_err(zip_err(source))?;
    let output = File::create(target).map_err(|e| LaunchError::io(target, e))?;
    let mut writer = zip::ZipWriter::new(output);
    let deflated = zip::write::SimpleFileOptions::default().compression_method(zip::CompressionMethod::Deflated);
    let mut found = false;
    for i in 0..archive.len() {
        let entry = archive.by_index_raw(i).map_err(zip_err(source))?;
        let is_target = entry.name().is_ok_and(|n| n == TARGET);
        if is_target {
            found = true;
            drop(entry);
            writer.start_file(TARGET, deflated).map_err(zip_err(target))?;
            writer
                .write_all(patch.linux_mouse)
                .map_err(|e| LaunchError::io(target, e))?;
        } else {
            writer.raw_copy_file(entry).map_err(zip_err(target))?; // unchanged bytes, no recompression
        }
    }
    if !found {
        return Err(LaunchError::Metadata(format!(
            "{TARGET} not found in {}",
            source.display()
        )));
    }
    writer.start_file(HELPER, deflated).map_err(zip_err(target))?;
    writer.write_all(patch.helper).map_err(|e| LaunchError::io(target, e))?;
    let file = writer.finish().map_err(zip_err(target))?;
    file.sync_all().map_err(|e| LaunchError::io(target, e))
}

/// Remove patches built from older LWJGL jars or patch versions (best effort).
fn delete_stale(dir: &Path, prefix: &str, keep: &Path) {
    let Ok(entries) = fs::read_dir(dir) else { return };
    for path in entries.flatten().map(|e| e.path()) {
        let name = path
            .file_name()
            .map(|n| n.to_string_lossy().into_owned())
            .unwrap_or_default();
        if path != keep && name.starts_with(prefix) && name.ends_with(".jar") {
            let _ = fs::remove_file(&path); // a leftover we can't delete is harmless
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn jar(path: &Path, entries: &[(&str, &[u8])]) {
        let mut w = zip::ZipWriter::new(File::create(path).unwrap());
        for (name, data) in entries {
            w.start_file(*name, zip::write::SimpleFileOptions::default()).unwrap();
            w.write_all(data).unwrap();
        }
        w.finish().unwrap();
    }

    fn read(path: &Path, name: &str) -> Vec<u8> {
        let mut a = zip::ZipArchive::new(File::open(path).unwrap()).unwrap();
        let mut out = Vec::new();
        a.by_name(name).unwrap().read_to_end(&mut out).unwrap();
        out
    }

    #[test]
    fn splices_patch_and_helper_and_reuses_result() {
        let dir = tempfile::tempdir().unwrap();
        let lwjgl = dir.path().join("lwjgl-2.9.4-nightly-20150209.jar");
        jar(&lwjgl, &[(TARGET, b"original"), ("org/lwjgl/Sys.class", b"sys")]);
        let sha1 = sha1_of(&lwjgl).unwrap();
        let patch = Patch {
            source_sha1: &sha1,
            linux_mouse: b"patched",
            helper: b"helper",
        };

        let out = prepare(&lwjgl, &dir.path().join("patches"), &patch).unwrap();
        assert_eq!(
            out.file_name().unwrap().to_string_lossy(),
            format!("lwjgl-2.9.4-nightly-20150209-xwayland-mouse-v3-{}.jar", &sha1[..12])
        );
        assert_eq!(read(&out, TARGET), b"patched");
        assert_eq!(read(&out, HELPER), b"helper");
        assert_eq!(read(&out, "org/lwjgl/Sys.class"), b"sys");

        let modified = fs::metadata(&out).unwrap().modified().unwrap();
        assert_eq!(prepare(&lwjgl, &dir.path().join("patches"), &patch).unwrap(), out);
        assert_eq!(
            fs::metadata(&out).unwrap().modified().unwrap(),
            modified,
            "reused, not rebuilt"
        );
    }

    #[test]
    fn refuses_a_different_lwjgl_jar() {
        let dir = tempfile::tempdir().unwrap();
        let lwjgl = dir.path().join("lwjgl-2.9.4.jar");
        jar(&lwjgl, &[(TARGET, b"something else")]);
        let patch = Patch {
            source_sha1: &"0".repeat(40),
            linux_mouse: b"p",
            helper: b"h",
        };
        assert!(matches!(
            prepare(&lwjgl, dir.path(), &patch),
            Err(LaunchError::UnknownLwjgl { .. })
        ));
    }

    #[test]
    fn failure_leaves_no_partial_file_and_old_patches_are_removed() {
        let dir = tempfile::tempdir().unwrap();
        let patches = dir.path().join("patches");
        fs::create_dir_all(&patches).unwrap();
        let lwjgl = dir.path().join("lwjgl-2.9.4.jar");
        jar(&lwjgl, &[("unrelated", b"x")]); // no LinuxMouse
        let sha1 = sha1_of(&lwjgl).unwrap();
        let patch = Patch {
            source_sha1: &sha1,
            linux_mouse: b"p",
            helper: b"h",
        };
        assert!(prepare(&lwjgl, &patches, &patch).is_err());
        assert_eq!(fs::read_dir(&patches).unwrap().count(), 0);

        fs::write(patches.join("lwjgl-2.9.4-xwayland-mouse-v2.jar"), "old").unwrap();
        jar(&lwjgl, &[(TARGET, b"original")]);
        let sha1 = sha1_of(&lwjgl).unwrap();
        let out = prepare(
            &lwjgl,
            &patches,
            &Patch {
                source_sha1: &sha1,
                ..patch
            },
        )
        .unwrap();
        let names: Vec<PathBuf> = fs::read_dir(&patches).unwrap().map(|e| e.unwrap().path()).collect();
        assert_eq!(names, [out]);
    }

    #[test]
    fn finds_lwjgl2_on_classpath() {
        let cp = [
            PathBuf::from("/x/lwjgl_util-2.9.4.jar"),
            PathBuf::from("/x/lwjgl-2.9.4-nightly-20150209.jar"),
            PathBuf::from("/x/forge.jar"),
        ];
        assert_eq!(find(&cp), Some(1));
        assert_eq!(find(&[PathBuf::from("/x/lwjgl-3.3.1.jar")]), None);
    }
}
