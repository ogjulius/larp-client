//! Shared foundations of the LARP launcher: which platform we're on, where files live, crash-safe
//! file writes, and the user's settings and accounts. Every on-disk format here is shared with the
//! Java launcher (see `testdata/launcher/golden/compat-*.json`), so both can run side by side.

pub mod accounts;
pub mod files;
pub mod platform;
pub mod safe_files;
pub mod secret;
pub mod settings;

pub use accounts::{Account, AccountKind, AccountStore};
pub use files::GameFiles;
pub use platform::{Dirs, Os};
pub use secret::Secret;
pub use settings::Settings;
