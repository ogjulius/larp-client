//! Files embedded at build time (see `build.rs`).

/// The LARP mod and the XWayland LWJGL patch, as produced by the Gradle build.
#[derive(Debug, Clone, Copy)]
pub struct Bundle {
    pub mod_jar: &'static [u8],
    /// `org/lwjgl/opengl/LarpWindowSizeCache.class`, compiled for Java 8.
    pub window_size_cache: &'static [u8],
    /// LWJGL's `org/lwjgl/opengl/LinuxMouse.class`, already patched.
    pub linux_mouse: &'static [u8],
    /// SHA-1 of the LWJGL jar `linux_mouse` was patched from.
    pub lwjgl_sha1: &'static str,
}

include!(concat!(env!("OUT_DIR"), "/bundle.rs"));

/// The embedded bundle, or [`crate::LaunchError::NoBundle`].
pub fn get() -> Result<&'static Bundle, crate::LaunchError> {
    BUNDLE.as_ref().ok_or(crate::LaunchError::NoBundle)
}
