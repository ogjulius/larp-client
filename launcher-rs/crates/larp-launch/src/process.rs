//! Starting and supervising the game process.

use crate::{LaunchError, log4j};
use larp_core::Os;
use std::path::Path;
use std::process::{ExitStatus, Stdio};
use std::sync::Arc;
use tokio::io::{AsyncBufReadExt, AsyncRead, BufReader};

/// Environment defaults for the game. Each is only set if the user hasn't set it already.
pub fn env_defaults(os: Os, threaded_gl: bool) -> Vec<(&'static str, &'static str)> {
    if os != Os::Linux {
        return Vec::new();
    }
    let mut env = Vec::new();
    if threaded_gl {
        env.push(("mesa_glthread", "true")); // Mesa (AMD / Intel)
        env.push(("__GL_THREADED_OPTIMIZATIONS", "1")); // NVIDIA proprietary
    }
    env.push(("__GL_SYNC_TO_VBLANK", "0")); // let the in-game VSync option decide
    env.push(("vblank_mode", "0"));
    env
}

/// A command for `cmd` (program + arguments) running in `instance`.
pub fn configure(
    cmd: &[String],
    instance: &Path,
    os: Os,
    threaded_gl: bool,
) -> Result<std::process::Command, LaunchError> {
    let (program, args) = cmd
        .split_first()
        .ok_or_else(|| LaunchError::Java("empty command".into()))?;
    std::fs::create_dir_all(instance).map_err(|e| LaunchError::io(instance, e))?;
    let mut command = std::process::Command::new(program);
    command.args(args).current_dir(instance);
    for (key, value) in env_defaults(os, threaded_gl) {
        if std::env::var_os(key).is_none() {
            command.env(key, value);
        }
    }
    Ok(command)
}

/// Run the game and pass each output line (log4j XML turned into readable lines) to `on_line`.
/// With `kill_on_drop`, dropping this future kills the game (the command line: Ctrl+C ends both).
/// Without it the game outlives the launcher, as the Java launcher's did when its window was closed.
pub async fn run_attached(
    command: std::process::Command,
    on_line: Arc<dyn Fn(&str) + Send + Sync>,
    kill_on_drop: bool,
) -> Result<ExitStatus, LaunchError> {
    let program = command.get_program().to_owned();
    let mut command = tokio::process::Command::from(command);
    command
        .stdin(Stdio::null())
        .stdout(Stdio::piped())
        .stderr(Stdio::piped())
        .kill_on_drop(kill_on_drop);
    let mut child = command.spawn().map_err(|e| LaunchError::io(&program, e))?;
    let stdout = child.stdout.take().map(|s| tokio::spawn(pump(s, on_line.clone())));
    let stderr = child.stderr.take().map(|s| tokio::spawn(pump(s, on_line)));
    let status = child.wait().await.map_err(|e| LaunchError::io(&program, e))?;
    for reader in [stdout, stderr].into_iter().flatten() {
        let _ = reader.await; // a reader can only end with EOF or a read error; the exit status is what counts
    }
    Ok(status)
}

async fn pump(stream: impl AsyncRead + Unpin, on_line: Arc<dyn Fn(&str) + Send + Sync>) {
    let mut reader = BufReader::new(stream);
    let mut formatter = log4j::Formatter::new();
    let mut buf = Vec::new();
    loop {
        buf.clear();
        match reader.read_until(b'\n', &mut buf).await {
            Ok(0) | Err(_) => break,
            Ok(_) => {
                let line = String::from_utf8_lossy(&buf);
                if let Some(out) = formatter.accept(line.trim_end_matches(['\n', '\r'])) {
                    on_line(&out);
                }
            }
        }
    }
}

/// Start the game independently of the launcher: output goes to `log_file`, so the launcher can exit.
pub fn start_detached(mut command: std::process::Command, log_file: &Path) -> Result<std::process::Child, LaunchError> {
    if let Some(parent) = log_file.parent() {
        std::fs::create_dir_all(parent).map_err(|e| LaunchError::io(parent, e))?;
    }
    let log = std::fs::File::create(log_file).map_err(|e| LaunchError::io(log_file, e))?;
    let err = log.try_clone().map_err(|e| LaunchError::io(log_file, e))?;
    command.stdin(Stdio::null()).stdout(log).stderr(err);
    command.spawn().map_err(|e| LaunchError::io(command.get_program(), e))
}

#[cfg(test)]
#[cfg(unix)]
mod tests {
    use super::*;
    use std::os::unix::fs::PermissionsExt;
    use std::sync::Mutex;
    use std::time::Duration;

    fn script(dir: &Path, body: &str) -> String {
        let path = dir.join("fake-java");
        std::fs::write(&path, format!("#!/bin/sh\n{body}\n")).unwrap();
        std::fs::set_permissions(&path, std::fs::Permissions::from_mode(0o755)).unwrap();
        path.display().to_string()
    }

    #[tokio::test]
    async fn attached_run_formats_output_and_reports_exit_code() {
        let dir = tempfile::tempdir().unwrap();
        let java = script(
            dir.path(),
            r#"echo '<log4j:Event logger="LARP" timestamp="0" level="INFO" thread="Client thread">'
echo '<log4j:Message><![CDATA[initialised 24 modules]]></log4j:Message>'
echo '</log4j:Event>'
echo "cwd=$(pwd)"
echo "oops" >&2
exit 3"#,
        );
        let cmd = configure(&[java], &dir.path().join("instance"), Os::Linux, true).unwrap();
        let lines = Arc::new(Mutex::new(Vec::new()));
        let sink = lines.clone();
        let status = run_attached(cmd, Arc::new(move |l: &str| sink.lock().unwrap().push(l.to_owned())), true)
            .await
            .unwrap();
        assert_eq!(status.code(), Some(3));
        let lines = lines.lock().unwrap();
        assert!(
            lines
                .iter()
                .any(|l| l.ends_with("[Client thread/INFO] (LARP) initialised 24 modules")),
            "{lines:?}"
        );
        assert!(
            lines.iter().any(|l| l.ends_with("/instance")),
            "runs in the instance dir: {lines:?}"
        );
        assert!(lines.contains(&"oops".to_owned()));
    }

    /// Drops `run_attached` while the game runs; returns whether the game finished its work anyway.
    async fn game_survives_drop(kill_on_drop: bool) -> bool {
        let dir = tempfile::tempdir().unwrap();
        let marker = dir.path().join("still-running");
        let java = script(dir.path(), &format!("sleep 0.4; touch '{}'", marker.display()));
        let cmd = configure(&[java], dir.path(), Os::Linux, false).unwrap();
        let run = run_attached(cmd, Arc::new(|_: &str| {}), kill_on_drop);
        assert!(tokio::time::timeout(Duration::from_millis(100), run).await.is_err());
        tokio::time::sleep(Duration::from_millis(900)).await;
        marker.exists()
    }

    #[tokio::test]
    async fn closing_the_graphical_launcher_leaves_the_game_running() {
        assert!(game_survives_drop(false).await);
    }

    #[tokio::test]
    async fn the_command_line_takes_the_game_down_with_it() {
        assert!(!game_survives_drop(true).await);
    }

    #[test]
    fn user_environment_wins_over_defaults() {
        assert!(env_defaults(Os::Linux, true).contains(&("mesa_glthread", "true")));
        assert!(
            !env_defaults(Os::Linux, false)
                .iter()
                .any(|(k, _)| *k == "mesa_glthread")
        );
        assert!(env_defaults(Os::Windows, true).is_empty());
    }

    #[tokio::test]
    async fn detached_run_writes_log_file() {
        let dir = tempfile::tempdir().unwrap();
        let java = script(dir.path(), "echo hello; echo err >&2");
        let cmd = configure(&[java], dir.path(), Os::Linux, false).unwrap();
        let log = dir.path().join("logs/launcher-output.log");
        let mut child = start_detached(cmd, &log).unwrap();
        assert!(child.wait().unwrap().success());
        let text = std::fs::read_to_string(log).unwrap();
        assert!(text.contains("hello") && text.contains("err"));
    }
}
