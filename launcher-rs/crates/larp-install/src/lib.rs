//! Installs Minecraft 1.8.9 with Forge into a [`larp_core::GameFiles`] layout. Every step is
//! idempotent and every downloaded file is verified, so a repeat run is only a verification pass.

mod error;
mod installer;
pub mod json;
pub mod libraries;
pub mod maven;
pub mod progress;
pub mod rules;

pub use error::InstallError;
pub use installer::{Endpoints, FORGE_INSTALL_PROFILE, FORGE_VERSION, InstalledGame, Installer};
pub use libraries::Library;
pub use progress::{Monotonic, Progress};
