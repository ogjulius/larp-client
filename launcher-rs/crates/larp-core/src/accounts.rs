//! Minecraft identities in `accounts.json` (same file and format as the Java launcher).
//! The file holds refresh tokens, so it is always written owner-only.

use crate::{Secret, safe_files};
use md5::{Digest, Md5};
use serde::{Deserialize, Serialize};
use std::io;
use std::path::{Path, PathBuf};

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "UPPERCASE")]
pub enum AccountKind {
    Offline,
    Microsoft,
}

fn offline_token() -> Secret {
    Secret::new("0")
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct Account {
    #[serde(rename = "type")]
    pub kind: AccountKind,
    pub username: String,
    /// Profile UUID without dashes.
    pub uuid: String,
    #[serde(default = "offline_token")]
    pub access_token: Secret,
    /// Unix time in milliseconds.
    #[serde(default)]
    pub access_token_expires_at: i64,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub refresh_token: Option<Secret>,
}

impl Account {
    /// An offline profile with the same UUID vanilla Minecraft derives: a name-based (v3) UUID of
    /// `"OfflinePlayer:" + name`, like Java's `UUID.nameUUIDFromBytes`.
    pub fn offline(username: &str) -> Self {
        let mut bytes: [u8; 16] = Md5::digest(format!("OfflinePlayer:{username}").as_bytes()).into();
        bytes[6] = (bytes[6] & 0x0f) | 0x30;
        bytes[8] = (bytes[8] & 0x3f) | 0x80;
        Self {
            kind: AccountKind::Offline,
            username: username.to_owned(),
            uuid: bytes.iter().map(|b| format!("{b:02x}")).collect(),
            access_token: offline_token(),
            access_token_expires_at: 0,
            refresh_token: None,
        }
    }

    /// 3–16 letters, digits or underscores (what Minecraft accepts).
    pub fn is_valid_username(name: &str) -> bool {
        (3..=16).contains(&name.len()) && name.bytes().all(|b| b.is_ascii_alphanumeric() || b == b'_')
    }

    pub const fn user_type(&self) -> &'static str {
        match self.kind {
            AccountKind::Microsoft => "msa",
            AccountKind::Offline => "legacy",
        }
    }

    /// True for a Microsoft account whose token expires within 5 minutes of `now_millis`.
    pub const fn needs_refresh(&self, now_millis: i64) -> bool {
        matches!(self.kind, AccountKind::Microsoft) && now_millis > self.access_token_expires_at - 5 * 60_000
    }

    fn is_complete(&self) -> bool {
        !self.uuid.is_empty() && !self.username.is_empty()
    }
}

#[derive(Serialize, Deserialize)]
struct Data<A> {
    accounts: Vec<A>,
    #[serde(default = "minus_one")]
    selected: i64,
}

const fn minus_one() -> i64 {
    -1
}

#[derive(Debug, Clone)]
pub struct AccountStore {
    file: PathBuf,
    accounts: Vec<Account>,
    selected: Option<usize>,
    problem: Option<String>,
}

impl AccountStore {
    /// Load `file`. A corrupt file is moved to `accounts.json.broken` (never overwritten: it may hold
    /// refresh tokens the user wants back) and reported in [`AccountStore::load_problem`].
    /// Entries that are null or incomplete are dropped; the rest are kept.
    pub fn load(file: &Path) -> Self {
        let mut store = Self {
            file: file.to_path_buf(),
            accounts: Vec::new(),
            selected: None,
            problem: None,
        };
        let text = match std::fs::read_to_string(file) {
            Ok(text) => text,
            Err(e) if e.kind() == io::ErrorKind::NotFound => return store,
            Err(e) => {
                store.problem = Some(format!("{} could not be read ({e}).", file.display()));
                return store;
            }
        };
        match serde_json::from_str::<Option<Data<serde_json::Value>>>(&text) {
            Ok(Some(data)) => {
                store.accounts = data
                    .accounts
                    .into_iter()
                    .filter_map(|v| serde_json::from_value::<Account>(v).ok())
                    .filter(Account::is_complete)
                    .collect();
                store.selected = usize::try_from(data.selected)
                    .ok()
                    .filter(|&i| i < store.accounts.len());
            }
            Ok(None) => store.problem = Some(safe_files::quarantine_after_load_failure(file, "file is empty")),
            Err(e) => {
                let reason = if text.trim().is_empty() {
                    "file is empty".to_owned()
                } else {
                    e.to_string()
                };
                store.problem = Some(safe_files::quarantine_after_load_failure(file, &reason));
            }
        }
        if store.selected.is_none() && !store.accounts.is_empty() {
            store.selected = Some(0);
        }
        store
    }

    pub fn load_problem(&self) -> Option<&str> {
        self.problem.as_deref()
    }

    pub fn all(&self) -> &[Account] {
        &self.accounts
    }

    pub fn selected(&self) -> Option<&Account> {
        self.selected.and_then(|i| self.accounts.get(i))
    }

    pub fn selected_mut(&mut self) -> Option<&mut Account> {
        self.selected.and_then(|i| self.accounts.get_mut(i))
    }

    /// Select by profile UUID; returns false if there is no such account.
    pub fn select(&mut self, uuid: &str) -> bool {
        match self.accounts.iter().position(|a| a.uuid == uuid) {
            Some(i) => {
                self.selected = Some(i);
                true
            }
            None => false,
        }
    }

    /// Add, replacing an account with the same UUID, and select it.
    pub fn add(&mut self, account: Account) {
        self.accounts.retain(|a| a.uuid != account.uuid);
        self.accounts.push(account);
        self.selected = Some(self.accounts.len() - 1);
    }

    pub fn remove(&mut self, uuid: &str) {
        let current = self.selected().map(|a| a.uuid.clone());
        self.accounts.retain(|a| a.uuid != uuid);
        self.selected = match current {
            Some(c) if c != uuid => self.accounts.iter().position(|a| a.uuid == c),
            _ => (!self.accounts.is_empty()).then_some(0),
        };
    }

    pub fn save(&self) -> io::Result<()> {
        let data = Data {
            accounts: self.accounts.iter().collect(),
            selected: self.selected.and_then(|i| i64::try_from(i).ok()).unwrap_or(-1),
        };
        let json = serde_json::to_string_pretty(&data).map_err(io::Error::other)?;
        safe_files::write_atomically(&self.file, json.as_bytes(), true)
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::fs;

    fn golden(name: &str) -> PathBuf {
        Path::new(env!("CARGO_MANIFEST_DIR"))
            .join("../../../testdata/launcher/golden")
            .join(name)
    }

    #[test]
    fn offline_uuid_matches_vanilla_and_java() {
        assert_eq!(Account::offline("Steve").uuid, "5627dd98e6be3c21b8a8e92344183641");
    }

    #[test]
    fn username_validation() {
        assert!(Account::is_valid_username("Steve_123"));
        assert!(!Account::is_valid_username("ab"));
        assert!(!Account::is_valid_username("seventeen_chars__"));
        assert!(!Account::is_valid_username("bad name"));
        assert!(!Account::is_valid_username("Stéve"));
    }

    #[test]
    fn reads_and_rewrites_java_accounts_file_identically() {
        let java = golden("compat-accounts.json");
        let store = AccountStore::load(&java);
        assert!(store.load_problem().is_none());
        assert_eq!(store.all().len(), 2);
        assert_eq!(store.selected().unwrap().username, "Steve");
        let alex = &store.all()[1];
        assert_eq!(alex.kind, AccountKind::Microsoft);
        assert_eq!(alex.refresh_token.as_ref().unwrap().expose(), "test-refresh-token");
        assert_eq!(alex.access_token_expires_at, 1_763_900_000_000);

        let dir = tempfile::tempdir().unwrap();
        let copy = dir.path().join("accounts.json");
        AccountStore {
            file: copy.clone(),
            ..store
        }
        .save()
        .unwrap();
        assert_eq!(
            fs::read_to_string(copy).unwrap().trim(),
            fs::read_to_string(java).unwrap().trim()
        );
    }

    #[cfg(unix)]
    #[test]
    fn saved_file_is_owner_only() {
        use std::os::unix::fs::PermissionsExt;
        let dir = tempfile::tempdir().unwrap();
        let file = dir.path().join("accounts.json");
        let mut store = AccountStore::load(&file);
        store.add(Account::offline("Steve"));
        store.save().unwrap();
        assert_eq!(fs::metadata(&file).unwrap().permissions().mode() & 0o777, 0o600);
    }

    #[test]
    fn corrupt_file_is_quarantined_not_overwritten() {
        let dir = tempfile::tempdir().unwrap();
        let file = dir.path().join("accounts.json");
        let corrupt = r#"{"accounts":[{"username":"Alice","refreshToken":"secret""#;
        fs::write(&file, corrupt).unwrap();
        let mut store = AccountStore::load(&file);
        assert!(store.all().is_empty());
        assert!(store.load_problem().is_some());
        store.add(Account::offline("Bob"));
        store.save().unwrap();
        assert_eq!(
            fs::read_to_string(dir.path().join("accounts.json.broken")).unwrap(),
            corrupt
        );
    }

    #[test]
    fn invalid_entries_are_dropped_and_selection_clamped() {
        let dir = tempfile::tempdir().unwrap();
        let file = dir.path().join("accounts.json");
        fs::write(
            &file,
            r#"{"accounts":[null,{"username":"NoUuid"},{"type":"OFFLINE","username":"Alice","uuid":"abc"}],"selected":5}"#,
        )
        .unwrap();
        let mut store = AccountStore::load(&file);
        assert_eq!(store.all().len(), 1);
        assert_eq!(store.selected().unwrap().username, "Alice");
        store.add(Account::offline("Bob"));
        assert_eq!(store.all().len(), 2);
    }

    #[test]
    fn add_replace_select_remove() {
        let dir = tempfile::tempdir().unwrap();
        let mut store = AccountStore::load(&dir.path().join("accounts.json"));
        store.add(Account::offline("Alice"));
        store.add(Account::offline("Bob"));
        store.add(Account::offline("Alice")); // replaces, moves to the end, selected
        assert_eq!(store.all().len(), 2);
        assert_eq!(store.selected().unwrap().username, "Alice");
        let bob = Account::offline("Bob").uuid;
        assert!(store.select(&bob));
        store.remove(&Account::offline("Alice").uuid);
        assert_eq!(store.selected().unwrap().username, "Bob");
        store.remove(&bob);
        assert!(store.selected().is_none());
    }

    #[test]
    fn refresh_window() {
        let mut a = Account::offline("Steve");
        assert!(!a.needs_refresh(i64::MAX));
        a.kind = AccountKind::Microsoft;
        a.access_token_expires_at = 1_000_000;
        assert!(!a.needs_refresh(1_000_000 - 5 * 60_000));
        assert!(a.needs_refresh(1_000_000 - 5 * 60_000 + 1));
    }
}
