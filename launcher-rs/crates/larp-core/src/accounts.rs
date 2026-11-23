//! Offline (non-premium) profiles in `accounts.json`, the same file and format as the Java launcher.
//! Microsoft accounts are not supported: an entry of another type (from an older version) is loaded as
//! an offline profile with the same name, and its tokens are dropped. The file is still written
//! owner-only, since older versions kept refresh tokens in it.

use crate::{Secret, safe_files};
use md5::{Digest, Md5};
use serde::{Deserialize, Serialize};
use std::io;
use std::path::{Path, PathBuf};

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "UPPERCASE")]
pub enum AccountKind {
    Offline,
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
    /// Always `"0"` for offline play; written so older launchers can read the file.
    #[serde(default = "offline_token")]
    pub access_token: Secret,
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
        }
    }

    /// 3–16 letters, digits or underscores (what Minecraft accepts).
    pub fn is_valid_username(name: &str) -> bool {
        (3..=16).contains(&name.len()) && name.bytes().all(|b| b.is_ascii_alphanumeric() || b == b'_')
    }

    /// Minecraft's `--userType` for an offline profile.
    pub const fn user_type(&self) -> &'static str {
        "legacy"
    }

    /// One entry of the file: an offline profile as stored, or, for any other type (a Microsoft
    /// account from an older version), a fresh offline profile with the same name. Entries without
    /// a name or UUID are dropped.
    fn from_entry(entry: serde_json::Value) -> Option<Self> {
        let offline = entry.get("type").and_then(serde_json::Value::as_str) == Some("OFFLINE");
        let account = if offline {
            serde_json::from_value::<Self>(entry).ok()?
        } else {
            let name = entry.get("username")?.as_str()?;
            entry.get("uuid")?.as_str().filter(|u| !u.is_empty())?;
            Self::offline(name)
        };
        (!account.uuid.is_empty() && !account.username.is_empty()).then_some(account)
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
    /// Load `file`. A corrupt file is moved to `accounts.json.broken` (never overwritten) and
    /// reported in [`AccountStore::load_problem`]. Entries that are null or incomplete are dropped,
    /// entries of another type become offline profiles (see [`Account::from_entry`]); duplicates
    /// that result keep their first position. A file that needed migrating is rewritten at once, so
    /// the old Microsoft tokens do not stay on disk.
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
                let migrate = data
                    .accounts
                    .iter()
                    .any(|e| e.is_object() && e.get("type").and_then(serde_json::Value::as_str) != Some("OFFLINE"));
                let entries: Vec<Option<Account>> = data.accounts.into_iter().map(Account::from_entry).collect();
                let wanted = usize::try_from(data.selected)
                    .ok()
                    .and_then(|i| entries.get(i).cloned().flatten())
                    .map(|a| a.uuid);
                for account in entries.into_iter().flatten() {
                    if !store.accounts.iter().any(|a| a.uuid == account.uuid) {
                        store.accounts.push(account);
                    }
                }
                store.selected = wanted.and_then(|uuid| store.accounts.iter().position(|a| a.uuid == uuid));
                if store.selected.is_none() && !store.accounts.is_empty() {
                    store.selected = Some(0);
                }
                if migrate && let Err(e) = store.save() {
                    store.problem = Some(format!(
                        "{} holds Microsoft sign-in tokens that are no longer used, and could not be rewritten without them ({e}).",
                        file.display()
                    ));
                }
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
        assert_eq!(store.all()[1].username, "Alex");

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

    /// Microsoft accounts from older versions become offline profiles with the same name, without
    /// their tokens, and stay selected; the result matches what the Java launcher writes.
    #[test]
    fn microsoft_accounts_become_offline_profiles() {
        let dir = tempfile::tempdir().unwrap();
        let file = dir.path().join("accounts.json");
        fs::copy(golden("legacy-microsoft-accounts.json"), &file).unwrap();
        let store = AccountStore::load(&file);
        assert!(store.load_problem().is_none());
        // Rewritten on load: the tokens are gone from disk without waiting for a save.
        assert_eq!(
            fs::read_to_string(&file).unwrap().trim(),
            fs::read_to_string(golden("legacy-microsoft-accounts.migrated.json"))
                .unwrap()
                .trim()
        );
        let names: Vec<_> = store.all().iter().map(|a| a.username.as_str()).collect();
        assert_eq!(names, ["Steve", "Alex"]);
        assert_eq!(store.all()[1], Account::offline("Alex"));
        assert_eq!(store.selected().unwrap().username, "Alex");

        store.save().unwrap();
        let saved = fs::read_to_string(&file).unwrap();
        assert!(!saved.contains("token-"), "tokens must not survive: {saved}");
        assert_eq!(
            saved.trim(),
            fs::read_to_string(golden("legacy-microsoft-accounts.migrated.json"))
                .unwrap()
                .trim()
        );
    }

    #[test]
    fn migration_merges_a_microsoft_account_into_the_offline_profile_of_the_same_name() {
        let dir = tempfile::tempdir().unwrap();
        let file = dir.path().join("accounts.json");
        fs::write(
            &file,
            r#"{"accounts":[{"type":"OFFLINE","username":"Steve","uuid":"5627dd98e6be3c21b8a8e92344183641"},
                {"type":"MICROSOFT","username":"Steve","uuid":"0123","refreshToken":"x"},
                {"type":"MICROSOFT","username":"NoUuid"}],"selected":1}"#,
        )
        .unwrap();
        let store = AccountStore::load(&file);
        assert_eq!(store.all(), [Account::offline("Steve")]);
        assert_eq!(store.selected().unwrap().username, "Steve");
    }
}
