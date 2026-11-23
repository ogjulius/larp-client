#![allow(clippy::unwrap_used, clippy::expect_used, clippy::panic)] // test code: failing loudly is the point

//! The launcher driven through its accessibility tree, like a user (or a screen reader) would, plus
//! image snapshots of every page (`UPDATE_SNAPSHOTS=1 cargo test -p larp-app` to accept changes;
//! compare them with the Java launcher in docs/rebuild/screenshots/).

use egui_kittest::kittest::Queryable;
use egui_kittest::{Harness, SnapshotResults};
use larp_app::backend::Effects;
use larp_app::model::{Event, Launch, Model, Page, Request};
use larp_app::ui::{App, WINDOW};
use larp_core::{AccountStore, GameFiles, Settings};
use std::path::{Path, PathBuf};
use std::sync::{Arc, Mutex};

/// Records requests; the test feeds events back.
#[derive(Default)]
struct Fake {
    requests: Vec<Request>,
    events: Vec<Event>,
}

struct FakeEffects(Arc<Mutex<Fake>>);

impl Effects for FakeEffects {
    fn submit(&mut self, request: Request) {
        self.0.lock().unwrap().requests.push(request);
    }

    fn poll(&mut self) -> Vec<Event> {
        std::mem::take(&mut self.0.lock().unwrap().events)
    }
}

struct Setup {
    dir: tempfile::TempDir,
    fake: Arc<Mutex<Fake>>,
}

impl Setup {
    /// A fresh config; `accounts` copies the shared compatibility fixture (offline profiles Steve
    /// and Alex).
    fn new(accounts: bool) -> Self {
        let dir = tempfile::tempdir().unwrap();
        std::fs::create_dir_all(dir.path().join("config")).unwrap();
        if accounts {
            let fixture =
                Path::new(env!("CARGO_MANIFEST_DIR")).join("../../../testdata/launcher/golden/compat-accounts.json");
            std::fs::copy(fixture, dir.path().join("config/accounts.json")).unwrap();
        }
        Self {
            dir,
            fake: Arc::default(),
        }
    }

    fn config(&self) -> PathBuf {
        self.dir.path().join("config")
    }

    fn harness(&self, page: Page) -> Harness<'static, App> {
        let model = Model::new(GameFiles::new(self.dir.path().join("data")), &self.config(), 16384);
        let effects = FakeEffects(self.fake.clone());
        let mut harness = Harness::builder().with_size(WINDOW).build_eframe(move |cc| {
            larp_app::theme::install(&cc.egui_ctx);
            App::new(model, Box::new(effects), page)
        });
        harness.run();
        harness
    }

    fn requests(&self) -> Vec<Request> {
        std::mem::take(&mut self.fake.lock().unwrap().requests)
    }

    fn send(&self, harness: &mut Harness<'_, App>, event: Event) {
        self.fake.lock().unwrap().events.push(event);
        harness.run();
    }
}

/// Snapshot without the pointer (no cursor drawn, no hover highlight).
fn snapshot(harness: &mut Harness<'_, App>, results: &mut SnapshotResults, name: &str) {
    harness.remove_cursor();
    harness.run();
    results.add(harness.try_snapshot(name));
}

/// Focus a text field by its accessible name and type into it, as a user would.
fn type_into(harness: &mut Harness<'_, App>, field: &str, text: &str) {
    harness.get_by_label(field).focus();
    harness.run();
    harness.get_by_label(field).type_text(text);
    harness.run();
}

#[test]
fn first_run_add_an_offline_profile_then_launch() {
    let setup = Setup::new(false);
    let mut harness = setup.harness(Page::Home);
    // Without an account the button offers to add one and leads to the Accounts page.
    harness.get_by_label("Add account").click();
    harness.run();
    assert_eq!(harness.state().model.page, Page::Accounts);

    assert!(
        harness.get_by_label("Offline username").is_focused(),
        "Launch without an account focuses the field"
    );
    harness.get_by_label("Offline username").type_text("Steve");
    harness.run();
    harness.get_by_label("Add").click();
    harness.run();
    // The first account goes straight back to Home, saved and selected.
    assert_eq!(harness.state().model.page, Page::Home);
    let saved = AccountStore::load(&setup.config().join("accounts.json"));
    assert_eq!(saved.selected().unwrap().username, "Steve");

    setup.requests();
    harness.get_by_label("Launch").click();
    harness.run_steps(3); // the indeterminate progress bar animates, so the UI never settles
    let requests = setup.requests();
    assert!(
        matches!(requests.as_slice(), [Request::Launch { account, .. }] if account.username == "Steve"),
        "{requests:?}"
    );
}

#[test]
fn invalid_username_is_explained() {
    let setup = Setup::new(false);
    let mut harness = setup.harness(Page::Accounts);
    type_into(&mut harness, "Offline username", "a b");
    assert_eq!(harness.state().model.offline_name, "a b");
    harness.get_by_label("Add").click();
    harness.run();
    let message = harness.state().model.accounts_message.clone().unwrap();
    assert!(message.error && message.text.contains("3–16 characters"), "{message:?}");
    assert!(
        AccountStore::load(&setup.config().join("accounts.json"))
            .all()
            .is_empty()
    );
}

#[test]
fn switching_and_removing_accounts() {
    let setup = Setup::new(true);
    let mut harness = setup.harness(Page::Accounts);
    harness.get_by_label("Use").click(); // only the inactive account (Alex) has one
    harness.run();
    let saved = AccountStore::load(&setup.config().join("accounts.json"));
    assert_eq!(saved.selected().unwrap().username, "Alex");

    harness.get_all_by_label("Remove").next().unwrap().click();
    harness.run();
    let saved = AccountStore::load(&setup.config().join("accounts.json"));
    assert_eq!(saved.all().len(), 1);
}

#[test]
fn navigation_rail_switches_pages() {
    let setup = Setup::new(true);
    let mut harness = setup.harness(Page::Home);
    for page in [Page::Accounts, Page::Settings, Page::Console, Page::Home] {
        harness.get_by_label(page.label()).click();
        harness.run();
        assert_eq!(harness.state().model.page, page);
    }
    setup.requests();
    harness.get_by_label("Files").click();
    harness.run();
    assert!(matches!(setup.requests().as_slice(), [Request::Open(path)] if path.ends_with("instance")));
}

#[test]
fn settings_are_saved_as_they_change() {
    let setup = Setup::new(true);
    let mut harness = setup.harness(Page::Settings);
    harness.get_by_label("Threaded OpenGL").click();
    harness.run();
    // Below the fold: scroll to it like a user would.
    harness.get_by_label("Keep launcher open").scroll_to_me();
    harness.run();
    harness.get_by_label("Keep launcher open").click();
    harness.run();
    let saved = Settings::load(&setup.config().join("launcher.json")).settings;
    assert!(!saved.threaded_gl);
    assert!(saved.keep_launcher_open);
}

#[test]
fn launch_progress_running_and_failure() {
    let setup = Setup::new(true);
    let mut harness = setup.harness(Page::Home);
    harness.get_by_label("Launch").click();
    harness.run_steps(3); // indeterminate progress animates until the first progress event
    assert!(matches!(setup.requests().as_slice(), [.., Request::Launch { .. }]));
    setup.send(
        &mut harness,
        Event::Progress {
            status: "Downloading assets".into(),
            fraction: Some(0.42),
        },
    );
    let mut snapshots = SnapshotResults::new();
    snapshot(&mut harness, &mut snapshots, "home_installing");
    setup.send(&mut harness, Event::Running);
    assert!(matches!(&harness.state().model.launch, Launch::Busy { label, .. } if label == "Running"));
    setup.send(
        &mut harness,
        Event::Finished {
            error: Some("Minecraft closed unexpectedly (code 1). Details are on the Console page.".into()),
        },
    );
    assert_eq!(harness.state().model.launch, Launch::Idle);
    snapshot(&mut harness, &mut snapshots, "home_failed");
}

/// Offline profiles only: there is no Microsoft sign-in anywhere in the launcher.
#[test]
fn there_is_no_microsoft_sign_in() {
    let setup = Setup::new(true);
    for page in Page::ALL {
        let harness = setup.harness(page);
        assert!(harness.query_by_label("Sign in").is_none(), "{page:?}");
        assert!(harness.query_by_label_contains("Microsoft").is_none(), "{page:?}");
    }
}

#[test]
fn unreadable_settings_are_reported_once() {
    let setup = Setup::new(true);
    std::fs::write(setup.config().join("launcher.json"), "{ not json").unwrap();
    let mut harness = setup.harness(Page::Home);
    // The folder is a random temporary directory: hide it from the image (masks last one frame).
    harness.remove_cursor();
    harness.run();
    let folder = harness.get_by_label_contains("Folder:").rect();
    // Random names differ in width, so mask a fixed width from the (fixed) left edge.
    harness.mask(folder.with_max_x(folder.min.x + 480.0));
    harness.snapshot("problems_dialog");
    harness.get_by_label("OK").click();
    harness.run();
    assert!(harness.state().model.problems.is_empty());
    assert!(
        setup.config().join("launcher.json.broken").exists(),
        "the broken file is kept for the user"
    );
}

#[test]
fn page_snapshots() {
    let setup = Setup::new(true);
    let mut snapshots = SnapshotResults::new();
    for page in Page::ALL {
        let mut harness = setup.harness(page);
        if page == Page::Console {
            for line in [
                "[launcher] Java: /usr/lib/jvm/java-8-openjdk/bin/java",
                "[12:00:01] [Client thread/INFO] (LARP) initialised 24 modules",
            ] {
                setup.send(&mut harness, Event::Log(line.into()));
            }
        }
        snapshot(
            &mut harness,
            &mut snapshots,
            &format!("page_{}", page.label().to_lowercase()),
        );
    }
}
