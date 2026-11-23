use std::path::PathBuf;

#[derive(Debug, thiserror::Error)]
pub enum LaunchError {
    #[error("JVM arguments: {0}. Fix them in Settings → Java.")]
    JvmArgs(String),
    #[error("{path}: {source}")]
    Io {
        path: PathBuf,
        #[source]
        source: std::io::Error,
    },
    #[error("{path}: {source}")]
    Zip {
        path: PathBuf,
        #[source]
        source: zip::result::ZipError,
    },
    #[error("unexpected LWJGL jar (SHA-1 {actual}); the XWayland mouse fix only knows LWJGL 2.9.4-nightly-20150209")]
    UnknownLwjgl { actual: String },
    #[error("{0}")]
    Java(String),
    #[error("unexpected metadata: {0}")]
    Metadata(String),
    #[error("this launcher build has no bundled LARP mod (build it with ./gradlew :launcher:rustBundle first)")]
    NoBundle,
    #[error(transparent)]
    Net(#[from] larp_net::NetError),
    #[error(transparent)]
    Install(#[from] larp_install::InstallError),
}

impl LaunchError {
    pub(crate) fn io(path: impl Into<PathBuf>, source: std::io::Error) -> Self {
        Self::Io {
            path: path.into(),
            source,
        }
    }
}
