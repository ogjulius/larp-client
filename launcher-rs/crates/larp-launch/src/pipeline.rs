//! The steps every launch takes, shared by the command line and the graphical launcher: install or
//! verify the game, find Java 8, set up the instance, apply the XWayland LWJGL fix and build the
//! command. Signing in (refreshing a Microsoft token) happens before this and is the caller's job.

use crate::java_runtime::JavaRuntime;
use crate::{LaunchError, bundle, command, instance, lwjgl};
use larp_core::{Account, GameFiles, Os, Settings};
use larp_install::{InstalledGame, Installer, Progress};
use larp_net::Net;
use std::path::{Path, PathBuf};

/// Everything needed to start the game.
#[derive(Debug)]
pub struct Prepared {
    pub game: InstalledGame,
    pub java: PathBuf,
    pub command: Vec<String>,
}

/// Install or verify the game and the Java runtime, then set up the instance directory. Returns the
/// installed game and the Java executable. `--install-only` stops here.
pub async fn install(
    net: &Net,
    files: &GameFiles,
    settings: &Settings,
    progress: &dyn Progress,
) -> Result<(InstalledGame, PathBuf), LaunchError> {
    let game = Installer::new(net.clone(), files.clone()).install(progress).await?;
    let java = JavaRuntime::new(net.clone(), files.clone())
        .resolve(&settings.java_path, progress)
        .await?;
    progress.update("Installing LARP", None);
    let mod_jar = bundle::get()?.mod_jar;
    let instance_files = files.clone();
    tokio::task::spawn_blocking(move || instance::prepare(&instance_files, mod_jar))
        .await
        .map_err(|e| LaunchError::Java(format!("instance setup was interrupted: {e}")))??;
    Ok((game, java))
}

/// [`install`], then build the launch command for `account`. `warn` receives problems that only cost
/// an optional fix (currently: the LWJGL mouse patch).
pub async fn prepare(
    net: &Net,
    files: &GameFiles,
    settings: &Settings,
    account: &Account,
    progress: &dyn Progress,
    warn: &(dyn Fn(&str) + Sync),
) -> Result<Prepared, LaunchError> {
    let (game, java) = install(net, files, settings, progress).await?;
    let os = Os::current();
    let classpath = if os == Os::Linux {
        match with_mouse_fix(&game.classpath, &files.patches).await {
            Ok(classpath) => classpath,
            Err(e) => {
                warn(&format!(
                    "LWJGL mouse patch unavailable, the mouse may lag under XWayland: {e}"
                ));
                game.classpath.clone()
            }
        }
    } else {
        game.classpath.clone()
    };
    let command = command::build(
        &game,
        files,
        account,
        settings,
        &java,
        &classpath,
        os.classpath_separator(),
    )?;
    Ok(Prepared { game, java, command })
}

/// The classpath with LWJGL swapped for the patched copy (XWayland mouse fix).
async fn with_mouse_fix(classpath: &[PathBuf], patches: &Path) -> Result<Vec<PathBuf>, LaunchError> {
    let mut classpath = classpath.to_vec();
    let Some(index) = lwjgl::find(&classpath) else {
        return Ok(classpath);
    };
    let bundle = bundle::get()?;
    let (jar, dir) = (classpath[index].clone(), patches.to_path_buf());
    let patched = tokio::task::spawn_blocking(move || {
        let patch = lwjgl::Patch {
            source_sha1: bundle.lwjgl_sha1,
            linux_mouse: bundle.linux_mouse,
            helper: bundle.window_size_cache,
        };
        lwjgl::prepare(&jar, &dir, &patch)
    })
    .await
    .map_err(|e| LaunchError::Java(format!("LWJGL patch was interrupted: {e}")))??;
    classpath[index] = patched;
    Ok(classpath)
}
