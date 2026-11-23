//! The launcher's state and behaviour, without any drawing. The UI calls the action methods and
//! renders the fields; side effects (network, processes, files, dialogs) leave as [`Request`]s for
//! the backend, whose results come back as [`Event`]s. That keeps every behaviour testable.

use crate::art::{self, Image};
use larp_core::{Account, AccountKind, AccountStore, GameFiles, Settings};
use std::collections::{HashMap, HashSet, VecDeque};
use std::path::PathBuf;
use std::sync::Arc;

#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash)]
pub enum Page {
    Home,
    Accounts,
    Settings,
    Console,
}

impl Page {
    pub const ALL: [Self; 4] = [Self::Home, Self::Accounts, Self::Settings, Self::Console];

    pub const fn label(self) -> &'static str {
        match self {
            Self::Home => "Home",
            Self::Accounts => "Accounts",
            Self::Settings => "Settings",
            Self::Console => "Console",
        }
    }

    /// `--page NAME` (case-insensitive).
    pub fn parse(name: &str) -> Option<Self> {
        Self::ALL.into_iter().find(|p| p.label().eq_ignore_ascii_case(name))
    }
}

/// The Launch button: idle with a label, or busy (it becomes its own progress bar).
#[derive(Debug, Clone, PartialEq)]
pub enum Launch {
    Idle,
    Busy {
        label: String,
        detail: String,
        /// 0..=1, or `None` while indeterminate.
        fraction: Option<f64>,
    },
}

impl Launch {
    fn busy(label: &str, detail: impl Into<String>, fraction: Option<f64>) -> Self {
        Self::Busy {
            label: label.to_owned(),
            detail: detail.into(),
            fraction,
        }
    }
}

/// The Microsoft sign-in dialog.
#[derive(Debug, Clone, PartialEq, Eq)]
pub enum SignIn {
    Requesting,
    Code { user_code: String, uri: String },
}

/// Work for the backend.
#[derive(Debug, Clone)]
pub enum Request {
    /// Refresh the sign-in if needed, install, and start the game.
    Launch {
        account: Account,
        settings: Box<Settings>,
    },
    SignIn {
        client_id: String,
    },
    CancelSignIn,
    /// Read the panorama and default skins from the client jar.
    LoadArt,
    FetchSkin {
        uuid: String,
    },
    /// Open a folder or URL with the desktop's default application.
    Open(String),
    PickJava,
}

/// Results from the backend.
#[derive(Debug, Clone)]
pub enum Event {
    Progress {
        status: String,
        fraction: Option<f64>,
    },
    Log(String),
    AccountRefreshed(Account),
    /// The game is running and the launcher stays open.
    Running,
    /// The game exited or the launch failed; `error` is shown under the Launch button.
    Finished {
        error: Option<String>,
    },
    /// The game was started detached and survived its first seconds: close the launcher.
    Quit,
    Art(art::GameArt),
    Skin {
        uuid: String,
        skin: Image,
    },
    DeviceCode {
        user_code: String,
        uri: String,
    },
    SignedIn(Account),
    /// Sign-in failed; `None` when the user cancelled.
    SignInFailed(Option<String>),
    JavaPicked(PathBuf),
}

/// Console output: the last [`Console::MAX_LINES`] lines.
#[derive(Debug, Default)]
pub struct Console {
    lines: VecDeque<String>,
}

impl Console {
    pub const MAX_LINES: usize = 5000;

    pub fn push(&mut self, line: impl Into<String>) {
        if self.lines.len() == Self::MAX_LINES {
            self.lines.pop_front();
        }
        self.lines.push_back(line.into());
    }

    pub fn clear(&mut self) {
        self.lines.clear();
    }

    pub fn lines(&self) -> impl Iterator<Item = &str> {
        self.lines.iter().map(String::as_str)
    }

    pub fn len(&self) -> usize {
        self.lines.len()
    }

    pub fn is_empty(&self) -> bool {
        self.lines.is_empty()
    }

    pub fn text(&self) -> String {
        let mut text = self.lines.iter().map(String::as_str).collect::<Vec<_>>().join("\n");
        if !text.is_empty() {
            text.push('\n');
        }
        text
    }
}

/// A message under a form: success (green) or error (red).
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Message {
    pub text: String,
    pub error: bool,
}

#[derive(Debug)]
pub struct Model {
    pub files: GameFiles,
    pub settings: Settings,
    settings_file: PathBuf,
    pub accounts: AccountStore,
    pub page: Page,
    pub launch: Launch,
    /// An error from the last launch, shown under the Launch button until the next refresh.
    pub home_error: Option<String>,
    pub console: Console,
    pub accounts_message: Option<Message>,
    pub offline_name: String,
    /// Set when Launch was pressed without an account: the UI focuses the username field once.
    pub focus_username: bool,
    pub sign_in: Option<SignIn>,
    /// Problems found while loading settings or accounts, shown once at start.
    pub problems: Vec<String>,
    pub art: art::GameArt,
    /// Bumped whenever `art` or `skins` change, so the UI re-uploads textures.
    pub art_generation: u64,
    skins: HashMap<String, Arc<Image>>,
    skins_requested: HashSet<String>,
    /// Total system memory, for the memory slider's range.
    pub total_memory_mb: u64,
    outbox: Vec<Request>,
}

impl Model {
    pub fn new(files: GameFiles, config_dir: &std::path::Path, total_memory_mb: u64) -> Self {
        let loaded = Settings::load(&config_dir.join("launcher.json"));
        let accounts = AccountStore::load(&config_dir.join("accounts.json"));
        let mut problems = Vec::new();
        problems.extend(loaded.problem.clone());
        problems.extend(accounts.load_problem().map(str::to_owned));
        let mut model = Self {
            files,
            settings: loaded.settings,
            settings_file: loaded.file,
            accounts,
            page: Page::Home,
            launch: Launch::Idle,
            home_error: None,
            console: Console::default(),
            accounts_message: None,
            offline_name: String::new(),
            focus_username: false,
            sign_in: None,
            problems,
            art: art::GameArt::default(),
            art_generation: 0,
            skins: HashMap::new(),
            skins_requested: HashSet::new(),
            total_memory_mb,
            outbox: vec![Request::LoadArt],
        };
        model.request_skins();
        model
    }

    /// Requests produced since the last call.
    pub fn take_requests(&mut self) -> Vec<Request> {
        std::mem::take(&mut self.outbox)
    }

    pub fn show(&mut self, page: Page) {
        self.page = page;
        if page == Page::Accounts {
            self.request_skins();
        }
    }

    /// The folder holding `launcher.json` and `accounts.json`.
    pub fn settings_folder(&self) -> Option<&std::path::Path> {
        self.settings_file.parent()
    }

    /// The memory slider's upper bound: total RAM minus 1 GB, within 2–16 GB (as the Java launcher).
    pub fn max_memory_mb(&self) -> i64 {
        let total = i64::try_from(self.total_memory_mb).unwrap_or(i64::MAX);
        total.saturating_sub(1024).clamp(2048, 16384)
    }

    // ---- Home

    pub fn launch_label(&self) -> &'static str {
        if self.accounts.selected().is_none() {
            "Add account"
        } else {
            "Launch"
        }
    }

    /// The line under the Launch button and whether it is an error.
    pub fn status_line(&self) -> (String, bool) {
        if self.launch != Launch::Idle {
            return (String::new(), false);
        }
        if let Some(error) = &self.home_error {
            return (error.clone(), true);
        }
        if self.accounts.selected().is_none() {
            ("Add an offline profile or Microsoft account to play".into(), false)
        } else {
            (
                format!("Ready  ·  {:.1} GB memory", self.settings.memory_mb as f64 / 1024.0),
                false,
            )
        }
    }

    pub fn launch_clicked(&mut self) {
        if self.launch != Launch::Idle {
            return;
        }
        let Some(account) = self.accounts.selected().cloned() else {
            self.show(Page::Accounts);
            self.focus_username = true;
            return;
        };
        self.home_error = None;
        self.launch = Launch::busy("Preparing", "Checking files", None);
        self.outbox.push(Request::Launch {
            account,
            settings: Box::new(self.settings.clone()),
        });
    }

    // ---- Accounts

    pub fn add_offline(&mut self) {
        let name = self.offline_name.trim().to_owned();
        if !Account::is_valid_username(&name) {
            self.message("Usernames are 3–16 characters: letters, numbers and underscores.", true);
            return;
        }
        let first = self.accounts.all().is_empty();
        self.accounts.add(Account::offline(&name));
        self.offline_name.clear();
        self.message(&format!("Added {name}."), false);
        self.save_accounts();
        if first {
            self.show(Page::Home); // first account: straight back to the Launch button
        }
    }

    pub fn select_account(&mut self, uuid: &str) {
        self.accounts.select(uuid);
        self.save_accounts();
    }

    pub fn remove_account(&mut self, uuid: &str) {
        self.accounts.remove(uuid);
        self.save_accounts();
    }

    pub fn sign_in_clicked(&mut self) {
        let client_id = self.settings.microsoft_client_id.trim().to_owned();
        if client_id.is_empty() {
            self.message(
                "Microsoft sign-in needs an approved Azure client ID — set it in Settings.",
                true,
            );
            return;
        }
        self.sign_in = Some(SignIn::Requesting);
        self.outbox.push(Request::SignIn { client_id });
    }

    pub fn cancel_sign_in(&mut self) {
        if self.sign_in.take().is_some() {
            self.outbox.push(Request::CancelSignIn);
        }
    }

    fn message(&mut self, text: &str, error: bool) {
        self.accounts_message = Some(Message {
            text: text.to_owned(),
            error,
        });
    }

    fn save_accounts(&mut self) {
        if let Err(e) = self.accounts.save() {
            self.message(&format!("Could not save accounts: {e}"), true);
        }
        self.home_error = None;
        self.request_skins();
    }

    /// The skin to draw for `account`: its own (Microsoft accounts, once fetched) or the default
    /// Steve/Alex skin the game would give it.
    pub fn skin(&self, account: Option<&Account>) -> Option<Arc<Image>> {
        if let Some(own) = account.and_then(|a| self.skins.get(&a.uuid)) {
            return Some(own.clone());
        }
        let slim = account.is_some_and(|a| art::is_slim(&a.uuid));
        let default = if slim { &self.art.alex } else { &self.art.steve };
        default.clone().map(Arc::new)
    }

    fn request_skins(&mut self) {
        for account in self.accounts.all() {
            if account.kind == AccountKind::Microsoft && self.skins_requested.insert(account.uuid.clone()) {
                self.outbox.push(Request::FetchSkin {
                    uuid: account.uuid.clone(),
                });
            }
        }
    }

    // ---- Settings

    /// Persist the settings after any change. Text fields keep exactly what was typed; the file gets
    /// the client ID trimmed (as the Java launcher saved it).
    pub fn settings_changed(&mut self) {
        let mut saved = self.settings.clone();
        saved.microsoft_client_id = saved.microsoft_client_id.trim().to_owned();
        if let Err(e) = saved.save(&self.settings_file) {
            self.console.push(format!("[launcher] Could not save settings: {e}"));
        }
    }

    pub fn open_game_folder(&mut self) {
        if let Err(e) = std::fs::create_dir_all(&self.files.instance) {
            self.console.push(format!(
                "[launcher] Could not create {}: {e}",
                self.files.instance.display()
            ));
        }
        self.outbox
            .push(Request::Open(self.files.instance.display().to_string()));
    }

    pub fn open_logs_folder(&mut self) {
        let logs = self.files.instance.join("logs");
        self.outbox.push(Request::Open(logs.display().to_string()));
    }

    pub fn open(&mut self, target: &str) {
        self.outbox.push(Request::Open(target.to_owned()));
    }

    pub fn pick_java(&mut self) {
        self.outbox.push(Request::PickJava);
    }

    // ---- Events

    /// Apply a backend event. Returns true if the launcher should close.
    pub fn handle(&mut self, event: Event) -> bool {
        match event {
            Event::Progress { status, fraction } => {
                let detail = match fraction {
                    Some(f) if (0.0..1.0).contains(&f) => format!("{status}  ·  {}%", (f * 100.0).floor()),
                    _ => status,
                };
                self.launch = Launch::busy("Preparing", detail, fraction);
            }
            Event::Log(line) => self.console.push(line),
            Event::AccountRefreshed(account) => {
                self.accounts.update(account);
                if let Err(e) = self.accounts.save() {
                    self.console.push(format!("[launcher] Could not save accounts: {e}"));
                }
            }
            Event::Running => self.launch = Launch::busy("Running", "Minecraft is open", Some(1.0)),
            Event::Finished { error } => {
                self.launch = Launch::Idle;
                self.home_error = error;
            }
            Event::Quit => return true,
            Event::Art(art) => {
                // A failed reload (e.g. the jar is being replaced) keeps what we already have.
                self.art.panorama = art.panorama.or(self.art.panorama.take());
                self.art.steve = art.steve.or(self.art.steve.take());
                self.art.alex = art.alex.or(self.art.alex.take());
                self.art_generation += 1;
            }
            Event::Skin { uuid, skin } => {
                self.skins.insert(uuid, Arc::new(skin));
                self.art_generation += 1;
            }
            Event::DeviceCode { user_code, uri } => {
                if self.sign_in.is_some() {
                    self.sign_in = Some(SignIn::Code { user_code, uri });
                }
            }
            Event::SignedIn(account) => {
                self.sign_in = None;
                let name = account.username.clone();
                self.accounts.add(account);
                self.save_accounts();
                self.message(&format!("Signed in as {name}."), false);
            }
            Event::SignInFailed(error) => {
                self.sign_in = None;
                if let Some(error) = error {
                    self.message(&error, true);
                }
            }
            Event::JavaPicked(path) => {
                self.settings.java_path = path.display().to_string();
                self.settings_changed();
            }
        }
        false
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn model() -> (tempfile::TempDir, Model) {
        let dir = tempfile::tempdir().unwrap();
        let model = Model::new(
            GameFiles::new(dir.path().join("data")),
            &dir.path().join("config"),
            16384,
        );
        (dir, model)
    }

    #[test]
    fn launch_without_account_goes_to_accounts() {
        let (_dir, mut m) = model();
        m.take_requests();
        assert_eq!(m.launch_label(), "Add account");
        m.launch_clicked();
        assert_eq!(m.page, Page::Accounts);
        assert!(m.focus_username);
        assert!(m.take_requests().is_empty());
    }

    #[test]
    fn first_offline_account_returns_home_and_is_saved() {
        let (dir, mut m) = model();
        m.show(Page::Accounts);
        m.offline_name = " ab ".into();
        m.add_offline();
        assert!(m.accounts_message.as_ref().unwrap().error);
        m.offline_name = "Steve".into();
        m.add_offline();
        assert_eq!(m.page, Page::Home);
        assert_eq!(m.offline_name, "");
        assert_eq!(m.launch_label(), "Launch");
        let saved = AccountStore::load(&dir.path().join("config/accounts.json"));
        assert_eq!(saved.selected().unwrap().username, "Steve");
        // A second account does not jump pages.
        m.show(Page::Accounts);
        m.offline_name = "Alex".into();
        m.add_offline();
        assert_eq!(m.page, Page::Accounts);
    }

    #[test]
    fn launch_flow() {
        let (_dir, mut m) = model();
        m.offline_name = "Dev".into();
        m.add_offline();
        m.take_requests();
        m.home_error = Some("old".into());
        m.launch_clicked();
        assert!(matches!(m.take_requests().as_slice(), [Request::Launch { account, .. }] if account.username == "Dev"));
        assert_eq!(m.status_line(), (String::new(), false));
        m.launch_clicked(); // ignored while busy
        assert!(m.take_requests().is_empty());

        m.handle(Event::Progress {
            status: "Downloading assets".into(),
            fraction: Some(0.426),
        });
        assert_eq!(
            m.launch,
            Launch::Busy {
                label: "Preparing".into(),
                detail: "Downloading assets  ·  42%".into(),
                fraction: Some(0.426)
            }
        );
        m.handle(Event::Progress {
            status: "Ready".into(),
            fraction: Some(1.0),
        });
        assert!(matches!(&m.launch, Launch::Busy { detail, .. } if detail == "Ready"));
        m.handle(Event::Running);
        assert!(matches!(&m.launch, Launch::Busy { label, .. } if label == "Running"));
        m.handle(Event::Finished {
            error: Some("Minecraft closed unexpectedly (code 1).".into()),
        });
        assert_eq!(m.launch, Launch::Idle);
        assert_eq!(
            m.status_line(),
            ("Minecraft closed unexpectedly (code 1).".into(), true)
        );
        assert!(!m.handle(Event::Finished { error: None }));
        assert_eq!(m.status_line(), ("Ready  ·  3.0 GB memory".into(), false));
        assert!(m.handle(Event::Quit));
    }

    #[test]
    fn sign_in_needs_a_client_id_and_can_be_cancelled() {
        let (_dir, mut m) = model();
        m.take_requests();
        m.sign_in_clicked();
        assert!(m.sign_in.is_none());
        assert!(m.accounts_message.as_ref().unwrap().text.contains("client ID"));

        m.settings.microsoft_client_id = " abc ".into();
        m.sign_in_clicked();
        assert!(matches!(m.take_requests().as_slice(), [Request::SignIn { client_id }] if client_id == "abc"));
        m.handle(Event::DeviceCode {
            user_code: "ABCD-1234".into(),
            uri: "https://microsoft.com/link".into(),
        });
        assert!(matches!(&m.sign_in, Some(SignIn::Code { user_code, .. }) if user_code == "ABCD-1234"));
        m.cancel_sign_in();
        assert!(matches!(m.take_requests().as_slice(), [Request::CancelSignIn]));
        // A late code after cancelling does not reopen the dialog; a cancelled error says nothing.
        m.handle(Event::DeviceCode {
            user_code: "late".into(),
            uri: String::new(),
        });
        assert!(m.sign_in.is_none());
        m.accounts_message = None;
        m.handle(Event::SignInFailed(None));
        assert!(m.accounts_message.is_none());
    }

    #[test]
    fn signed_in_account_is_added_selected_and_its_skin_requested() {
        let (_dir, mut m) = model();
        m.take_requests();
        let mut account = Account::offline("Alex");
        account.kind = AccountKind::Microsoft;
        m.handle(Event::SignedIn(account.clone()));
        assert_eq!(m.accounts.selected().unwrap().uuid, account.uuid);
        assert!(matches!(m.take_requests().as_slice(), [Request::FetchSkin { uuid }] if *uuid == account.uuid));
        assert_eq!(m.accounts_message.as_ref().unwrap().text, "Signed in as Alex.");
    }

    #[test]
    fn refreshed_account_is_written_back_in_place() {
        let (dir, mut m) = model();
        for name in ["One", "Two"] {
            m.offline_name = name.into();
            m.add_offline();
        }
        let mut refreshed = Account::offline("One");
        refreshed.access_token_expires_at = 99;
        m.handle(Event::AccountRefreshed(refreshed));
        let saved = AccountStore::load(&dir.path().join("config/accounts.json"));
        assert_eq!(saved.all()[0].access_token_expires_at, 99);
        assert_eq!(saved.selected().unwrap().username, "Two");
    }

    #[test]
    fn skins_fall_back_to_the_default_for_the_profile() {
        let (_dir, mut m) = model();
        let steve = Image::new(64, 64);
        let mut alex = Image::new(64, 64);
        alex.set(0, 0, [1, 2, 3, 4]);
        m.handle(Event::Art(art::GameArt {
            panorama: None,
            steve: Some(steve.clone()),
            alex: Some(alex.clone()),
        }));
        // Offline "Steve" gets the slim (Alex) skin, as in the game.
        assert_eq!(*m.skin(Some(&Account::offline("Steve"))).unwrap(), alex);
        assert_eq!(*m.skin(Some(&Account::offline("Dev"))).unwrap(), steve);
        let own = Image::new(64, 32);
        m.handle(Event::Skin {
            uuid: Account::offline("Dev").uuid,
            skin: own.clone(),
        });
        assert_eq!(*m.skin(Some(&Account::offline("Dev"))).unwrap(), own);
        // A failed art reload keeps the old art.
        m.handle(Event::Art(art::GameArt::default()));
        assert!(m.art.steve.is_some());
    }

    #[test]
    fn settings_are_saved_on_change() {
        let (dir, mut m) = model();
        m.settings.memory_mb = 4096;
        m.settings_changed();
        let loaded = Settings::load(&dir.path().join("config/launcher.json"));
        assert_eq!(loaded.settings.memory_mb, 4096);
        m.handle(Event::JavaPicked(PathBuf::from("/opt/java8/bin/java")));
        let loaded = Settings::load(&dir.path().join("config/launcher.json"));
        assert_eq!(loaded.settings.java_path, "/opt/java8/bin/java");
    }

    #[test]
    fn memory_range_follows_system_memory() {
        let (_dir, mut m) = model();
        assert_eq!(m.max_memory_mb(), 15360);
        m.total_memory_mb = 2048;
        assert_eq!(m.max_memory_mb(), 2048);
        m.total_memory_mb = 65536;
        assert_eq!(m.max_memory_mb(), 16384);
    }

    #[test]
    fn console_keeps_the_last_lines() {
        let mut c = Console::default();
        for i in 0..Console::MAX_LINES + 10 {
            c.push(i.to_string());
        }
        assert_eq!(c.len(), Console::MAX_LINES);
        assert_eq!(c.lines().next(), Some("10"));
        assert!(c.text().ends_with(&format!("{}\n", Console::MAX_LINES + 9)));
        c.clear();
        assert!(c.is_empty() && c.text().is_empty());
    }

    #[test]
    fn load_problems_are_reported() {
        let dir = tempfile::tempdir().unwrap();
        std::fs::create_dir_all(dir.path().join("config")).unwrap();
        std::fs::write(dir.path().join("config/launcher.json"), "{ broken").unwrap();
        let m = Model::new(
            GameFiles::new(dir.path().join("data")),
            &dir.path().join("config"),
            8192,
        );
        assert_eq!(m.problems.len(), 1);
    }

    #[test]
    fn pages_parse_case_insensitively() {
        assert_eq!(Page::parse("settings"), Some(Page::Settings));
        assert_eq!(Page::parse("nope"), None);
    }
}
