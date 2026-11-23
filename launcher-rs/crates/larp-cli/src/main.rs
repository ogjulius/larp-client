//! `larp-client`: the LARP launcher's headless mode, with the same flags as the Java launcher.
#![allow(clippy::print_stdout)] // a command-line program reports on stdout

mod args;

use args::{Args, Mode};
use larp_auth::MicrosoftAuth;
use larp_core::{Account, AccountStore, Dirs, GameFiles, Os, Settings};
use larp_install::{Installer, Progress};
use larp_launch::java_runtime::JavaRuntime;
use larp_launch::{bundle, command, instance, lwjgl, process};
use larp_net::Net;
use std::error::Error;
use std::path::PathBuf;
use std::process::ExitCode;
use std::sync::{Arc, Mutex};
use std::time::{SystemTime, UNIX_EPOCH};

fn main() -> ExitCode {
    let args = match Args::parse(std::env::args().skip(1)) {
        Ok(args) => args,
        Err(message) => {
            eprintln!("larp-client: {message}\n\n{}", args::USAGE);
            return ExitCode::from(2);
        }
    };
    let runtime = match tokio::runtime::Builder::new_multi_thread().enable_all().build() {
        Ok(rt) => rt,
        Err(e) => {
            eprintln!("larp-client: cannot start: {e}");
            return ExitCode::FAILURE;
        }
    };
    match runtime.block_on(run(args)) {
        Ok(code) => code,
        Err(e) => {
            eprintln!("[larp] error: {e}");
            let mut source = e.source();
            while let Some(cause) = source {
                eprintln!("[larp]   caused by: {cause}");
                source = cause.source();
            }
            ExitCode::FAILURE
        }
    }
}

/// Prints `[larp] <status> <percent>%`, at most once per 10 % step of each status.
#[derive(Default)]
struct Printer(Mutex<(String, i64)>);

impl Progress for Printer {
    fn update(&self, status: &str, fraction: Option<f64>) {
        #[allow(clippy::cast_possible_truncation)] // 0..=100
        let percent = fraction.map_or(-1, |f| (f.clamp(0.0, 1.0) * 100.0) as i64);
        let mut last = self.0.lock().unwrap_or_else(std::sync::PoisonError::into_inner);
        if status != last.0 || percent / 10 != last.1 / 10 {
            if percent >= 0 {
                println!("[larp] {status} {percent}%");
            } else {
                println!("[larp] {status}");
            }
            *last = (status.to_owned(), percent);
        }
    }
}

fn now_millis() -> i64 {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map_or(0, |d| i64::try_from(d.as_millis()).unwrap_or(i64::MAX))
}

async fn run(args: Args) -> Result<ExitCode, Box<dyn Error>> {
    match args.mode {
        Mode::Help => {
            println!("{}", args::USAGE);
            return Ok(ExitCode::SUCCESS);
        }
        Mode::Version => {
            println!("larp-client {}", env!("CARGO_PKG_VERSION"));
            return Ok(ExitCode::SUCCESS);
        }
        Mode::Gui => {
            eprintln!(
                "This build has no graphical launcher yet. Use --launch or --install-only.\n\n{}",
                args::USAGE
            );
            return Ok(ExitCode::from(2));
        }
        Mode::Install | Mode::Launch => {}
    }

    let dirs = Dirs::from_env().ok_or("cannot determine the home directory")?;
    let loaded = Settings::load(&dirs.config.join("launcher.json"));
    if let Some(problem) = &loaded.problem {
        eprintln!("[larp] warning: {problem}");
    }
    let settings = loaded.settings;
    let mut accounts = AccountStore::load(&dirs.config.join("accounts.json"));
    if let Some(problem) = accounts.load_problem() {
        eprintln!("[larp] warning: {problem}");
    }
    let files = GameFiles::new(&dirs.data);
    let net = Net::new()?;
    let printer = Printer::default();

    let game = Installer::new(net.clone(), files.clone()).install(&printer).await?;
    let java = JavaRuntime::new(net.clone(), files.clone())
        .resolve(&settings.java_path, &printer)
        .await?;
    let bundle = bundle::get()?;
    instance::prepare(&files, bundle.mod_jar)?;
    println!("[larp] Java: {}", java.display());
    println!("[larp] Installed into {}", files.root.display());
    if args.mode == Mode::Install {
        return Ok(ExitCode::SUCCESS);
    }

    let account = match &args.offline {
        Some(name) => {
            if !Account::is_valid_username(name) {
                eprintln!("Invalid username: {name}");
                return Ok(ExitCode::from(2));
            }
            Account::offline(name)
        }
        None => {
            let Some(account) = accounts.selected_mut() else {
                eprintln!("No account selected; pass --offline NAME or add one in the launcher");
                return Ok(ExitCode::from(2));
            };
            if account.needs_refresh(now_millis()) {
                println!("[larp] Refreshing Microsoft sign-in");
                MicrosoftAuth::new(&settings.microsoft_client_id, net.clone())?
                    .refresh(account)
                    .await?;
                let account = account.clone();
                accounts.save()?;
                account
            } else {
                account.clone()
            }
        }
    };

    let classpath = classpath_with_mouse_fix(&game.classpath, &files, bundle).await;
    let cmd = command::build(
        &game,
        &files,
        &account,
        &settings,
        &java,
        &classpath,
        Os::current().classpath_separator(),
    )?;
    println!("[larp] {}", command::redact(&cmd, &account));
    let command = process::configure(&cmd, &files.instance, Os::current(), settings.threaded_gl)?;
    let status = process::run_attached(command, Arc::new(|line: &str| println!("{line}"))).await?;
    Ok(status
        .code()
        .and_then(|c| u8::try_from(c).ok())
        .map_or(ExitCode::FAILURE, ExitCode::from))
}

/// On Linux, swap LWJGL for the patched copy (XWayland mouse fix). Any problem only costs the fix.
async fn classpath_with_mouse_fix(
    classpath: &[PathBuf],
    files: &GameFiles,
    bundle: &'static bundle::Bundle,
) -> Vec<PathBuf> {
    let mut classpath = classpath.to_vec();
    let Some(index) = lwjgl::find(&classpath).filter(|_| Os::current() == Os::Linux) else {
        return classpath;
    };
    let (jar, dir) = (classpath[index].clone(), files.patches.clone());
    let patch = lwjgl::Patch {
        source_sha1: bundle.lwjgl_sha1,
        linux_mouse: bundle.linux_mouse,
        helper: bundle.window_size_cache,
    };
    match tokio::task::spawn_blocking(move || lwjgl::prepare(&jar, &dir, &patch)).await {
        Ok(Ok(patched)) => classpath[index] = patched,
        Ok(Err(e)) => eprintln!("[larp] LWJGL mouse patch unavailable, mouse may lag under XWayland: {e}"),
        Err(e) => eprintln!("[larp] LWJGL mouse patch unavailable, mouse may lag under XWayland: {e}"),
    }
    classpath
}
