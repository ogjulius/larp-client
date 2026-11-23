//! Everything between "the game is installed" and "the game is running": finding Java 8, setting up
//! the instance directory, the XWayland LWJGL fix, building the command line and running the process.

pub mod bundle;
pub mod command;
mod error;
pub mod instance;
pub mod java_runtime;
pub mod jvm_args;
pub mod log4j;
pub mod lwjgl;
pub mod pipeline;
pub mod process;

pub use error::LaunchError;
