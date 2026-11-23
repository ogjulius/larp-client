//! The real side effects behind [`Request`]s: launches, artwork, file dialogs and opening folders,
//! run on a tokio runtime. Results go back to the UI thread over a bounded channel, and
//! each one wakes the UI.

use crate::art;
use crate::model::{Event, Request};
use eframe::egui;
use larp_core::{Account, GameFiles, Os, Settings};
use larp_launch::{command, pipeline, process};
use larp_net::Net;
use std::path::{Path, PathBuf};
use std::sync::mpsc::{Receiver, SyncSender};
use std::sync::{Arc, mpsc};
use std::time::Duration;

/// What the UI needs from a backend; tests substitute a recording fake.
pub trait Effects {
    fn submit(&mut self, request: Request);
    fn poll(&mut self) -> Vec<Event>;
}

/// How long a detached game is watched for an instant crash (bad JVM arguments, wrong Java) before
/// the launcher closes.
const CRASH_GRACE: Duration = Duration::from_secs(4);
/// Queue between the workers and the UI. Game output is the only high-volume producer; the UI
/// drains the queue every frame, so a full queue only briefly slows the game's log pump.
const QUEUE: usize = 4096;

/// Sends events to the UI and wakes it.
#[derive(Clone)]
struct Sink {
    tx: SyncSender<Event>,
    ctx: egui::Context,
}

impl Sink {
    fn send(&self, event: Event) {
        if self.tx.send(event).is_ok() {
            self.ctx.request_repaint();
        }
    }

    fn log(&self, line: impl Into<String>) {
        self.send(Event::Log(line.into()));
    }
}

pub struct Backend {
    runtime: tokio::runtime::Runtime,
    sink: Sink,
    rx: Receiver<Event>,
    net: Result<Net, String>,
    files: GameFiles,
}

impl std::fmt::Debug for Backend {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.debug_struct("Backend")
            .field("files", &self.files.root)
            .finish_non_exhaustive()
    }
}

impl Backend {
    pub fn new(ctx: egui::Context, files: GameFiles) -> std::io::Result<Self> {
        let runtime = tokio::runtime::Builder::new_multi_thread()
            .enable_all()
            .thread_name("larp-worker")
            .build()?;
        let (tx, rx) = mpsc::sync_channel(QUEUE);
        Ok(Self {
            runtime,
            sink: Sink { tx, ctx },
            rx,
            net: Net::new().map_err(|e| e.to_string()),
            files,
        })
    }

    fn net(&self) -> Result<Net, String> {
        self.net.clone().map_err(|e| format!("Cannot use the network: {e}"))
    }
}

impl Effects for Backend {
    fn submit(&mut self, request: Request) {
        let sink = self.sink.clone();
        let files = self.files.clone();
        match request {
            Request::Launch { account, settings } => match self.net() {
                Ok(net) => {
                    self.runtime.spawn(launch(sink, net, files, account, *settings));
                }
                Err(e) => sink.send(Event::Finished { error: Some(e) }),
            },
            Request::LoadArt => {
                self.runtime
                    .spawn_blocking(move || sink.send(Event::Art(art::load(&files))));
            }
            Request::Open(target) => open(&target, &sink),
            Request::PickJava => {
                self.runtime.spawn(async move {
                    let start = if Path::new("/usr/lib/jvm").is_dir() {
                        "/usr/lib/jvm"
                    } else {
                        "/"
                    };
                    let picked = rfd::AsyncFileDialog::new()
                        .set_title("Choose a Java 8 executable")
                        .set_directory(start)
                        .pick_file()
                        .await;
                    if let Some(file) = picked {
                        sink.send(Event::JavaPicked(file.path().to_path_buf()));
                    }
                });
            }
        }
    }

    fn poll(&mut self) -> Vec<Event> {
        let mut events = Vec::new();
        while let Ok(event) = self.rx.try_recv() {
            events.push(event);
        }
        events
    }
}

fn describe_exit(status: std::process::ExitStatus) -> String {
    status
        .code()
        .map_or_else(|| "killed by a signal".to_owned(), |c| format!("code {c}"))
}

async fn launch(sink: Sink, net: Net, files: GameFiles, account: Account, settings: Settings) {
    if let Err(e) = try_launch(&sink, &net, &files, account, &settings).await {
        sink.log(format!("[launcher] Launch failed: {e}"));
        sink.send(Event::Finished { error: Some(e) });
    }
}

async fn try_launch(
    sink: &Sink,
    net: &Net,
    files: &GameFiles,
    account: Account,
    settings: &Settings,
) -> Result<(), String> {
    let progress = |status: &str, fraction: Option<f64>| {
        sink.send(Event::Progress {
            status: status.to_owned(),
            fraction,
        });
    };
    let warn = |warning: &str| sink.log(format!("[launcher] {warning}"));
    let prepared = pipeline::prepare(net, files, settings, &account, &progress, &warn)
        .await
        .map_err(|e| e.to_string())?;
    // First install: the panorama and default skins exist now.
    let art_files = files.clone();
    if let Ok(art) = tokio::task::spawn_blocking(move || art::load(&art_files)).await {
        sink.send(Event::Art(art));
    }
    sink.log(format!("[launcher] Java: {}", prepared.java.display()));
    sink.log(format!("[launcher] {}", command::redact(&prepared.command, &account)));
    progress("Starting Minecraft", None);
    let cmd = process::configure(&prepared.command, &files.instance, Os::current(), settings.threaded_gl)
        .map_err(|e| e.to_string())?;

    if !settings.keep_launcher_open {
        // Hand Minecraft off and quit. Output goes to a file because no one will read the pipe.
        let log = files.instance.join("logs").join("launcher-output.log");
        let mut child = process::start_detached(cmd, &log).map_err(|e| e.to_string())?;
        sink.log(format!("[launcher] Game output: {}", log.display()));
        let deadline = tokio::time::Instant::now() + CRASH_GRACE;
        while tokio::time::Instant::now() < deadline {
            if let Ok(Some(status)) = child.try_wait() {
                let exit = describe_exit(status);
                sink.log(format!(
                    "[launcher] Minecraft exited with {exit} (see {})",
                    log.display()
                ));
                sink.send(Event::Finished {
                    error: Some(format!("Minecraft closed right away ({exit}). Log: {}", log.display())),
                });
                return Ok(());
            }
            tokio::time::sleep(Duration::from_millis(100)).await;
        }
        sink.send(Event::Quit);
        return Ok(());
    }

    sink.send(Event::Running);
    let lines = sink.clone();
    // The game keeps running if the launcher window is closed (as with the Java launcher).
    let status = process::run_attached(cmd, Arc::new(move |line: &str| lines.log(line)), false)
        .await
        .map_err(|e| e.to_string())?;
    let exit = describe_exit(status);
    sink.log(format!("[launcher] Minecraft exited with {exit}"));
    sink.send(Event::Finished {
        error: (!status.success())
            .then(|| format!("Minecraft closed unexpectedly ({exit}). Details are on the Console page.")),
    });
    Ok(())
}

/// Open a folder or URL with the desktop's default application.
fn open(target: &str, sink: &Sink) {
    let mut command = match Os::current() {
        Os::Linux => std::process::Command::new("xdg-open"),
        Os::Osx => std::process::Command::new("open"),
        Os::Windows => std::process::Command::new("explorer"),
    };
    command
        .arg(target)
        .stdin(std::process::Stdio::null())
        .stdout(std::process::Stdio::null())
        .stderr(std::process::Stdio::null());
    match command.spawn() {
        // Reap the opener in the background so it never lingers as a zombie.
        Ok(mut child) => {
            std::thread::spawn(move || child.wait());
        }
        Err(e) => sink.log(format!("[launcher] Could not open {target}: {e}")),
    }
}

/// Total system memory in MB (8 GB if unknown).
pub fn total_memory_mb() -> u64 {
    let system = sysinfo::System::new_with_specifics(
        sysinfo::RefreshKind::nothing().with_memory(sysinfo::MemoryRefreshKind::nothing().with_ram()),
    );
    match system.map_or(0, |s| s.total_memory()) / (1024 * 1024) {
        0 => 8192,
        mb => mb,
    }
}

/// Where the launcher keeps its data and configuration.
pub fn dirs() -> Option<(GameFiles, PathBuf)> {
    let dirs = larp_core::Dirs::from_env()?;
    Some((GameFiles::new(&dirs.data), dirs.config))
}
