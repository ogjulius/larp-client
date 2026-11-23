//! The game's command line (same output as the Java launcher; golden file `command-linux.json`).

use crate::{LaunchError, jvm_args};
use larp_core::{Account, GameFiles, Settings};
use larp_install::InstalledGame;
use std::collections::HashMap;
use std::path::{Path, PathBuf};

/// Build the full command. `classpath` is passed separately from `game.classpath` so the caller can
/// swap in the patched LWJGL jar.
pub fn build(
    game: &InstalledGame,
    files: &GameFiles,
    account: &Account,
    settings: &Settings,
    java: &Path,
    classpath: &[PathBuf],
    separator: &str,
) -> Result<Vec<String>, LaunchError> {
    let mut cmd = vec![
        java.display().to_string(),
        format!("-Xmx{}M", settings.memory_mb),
        // Fixed heap size: no resize pauses while playing.
        format!("-Xms{}M", settings.memory_mb),
    ];
    cmd.extend(jvm_args::split(&settings.jvm_args).map_err(LaunchError::JvmArgs)?);
    cmd.push(format!("-Djava.library.path={}", game.natives_dir.display()));
    cmd.push("-Dfml.ignoreInvalidMinecraftCertificates=true".into());
    cmd.push("-Dfml.ignorePatchDiscrepancies=true".into());
    cmd.push("-Dminecraft.launcher.brand=larpclient".into());
    if settings.fullscreen {
        // The mod asks the compositor for fullscreen; LWJGL's own --fullscreen breaks under XWayland.
        cmd.push("-Dlarp.startFullscreen=true".into());
    }
    if let (Some(argument), Some(config)) = (&game.logging_argument, &game.logging_config) {
        // Mojang's patched log4j config (mitigates Log4Shell on 1.8.9's log4j 2.0-beta9).
        cmd.push(argument.replace("${path}", &config.display().to_string()));
    }
    cmd.push("-cp".into());
    cmd.push(
        classpath
            .iter()
            .map(|p| p.display().to_string())
            .collect::<Vec<_>>()
            .join(separator),
    );
    cmd.push(game.main_class.clone());

    let vars: HashMap<&str, String> = HashMap::from([
        ("auth_player_name", account.username.clone()),
        ("version_name", game.version_id.clone()),
        ("game_directory", files.instance.display().to_string()),
        ("assets_root", files.assets.display().to_string()),
        ("assets_index_name", game.asset_index.clone()),
        ("auth_uuid", account.uuid.clone()),
        ("auth_access_token", account.access_token.expose().to_owned()),
        ("user_properties", "{}".into()),
        ("user_type", account.user_type().into()),
    ]);
    // Like Java's String.split(" "): empty pieces stay, trailing empty pieces are dropped.
    let mut pieces: Vec<&str> = game.minecraft_arguments.split(' ').collect();
    while pieces.last() == Some(&"") {
        pieces.pop();
    }
    cmd.extend(pieces.into_iter().map(|arg| substitute(arg, &vars)));
    cmd.extend([
        "--width".into(),
        settings.width.to_string(),
        "--height".into(),
        settings.height.to_string(),
    ]);
    Ok(cmd)
}

/// `${name}` → its value; unknown placeholders and other arguments are kept as they are.
pub fn substitute(arg: &str, vars: &HashMap<&str, String>) -> String {
    arg.strip_prefix("${")
        .and_then(|a| a.strip_suffix('}'))
        .and_then(|name| vars.get(name))
        .cloned()
        .unwrap_or_else(|| arg.to_owned())
}

/// The command as one line for display, with the access token hidden.
pub fn redact(command: &[String], account: &Account) -> String {
    let joined = command.join(" ");
    let token = account.access_token.expose();
    if token.len() > 4 {
        joined.replace(token, "<token>")
    } else {
        joined
    }
}
