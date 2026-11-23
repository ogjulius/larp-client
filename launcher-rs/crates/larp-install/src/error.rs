use std::path::PathBuf;

#[derive(Debug, thiserror::Error)]
pub enum InstallError {
    #[error(transparent)]
    Net(#[from] larp_net::NetError),
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
    /// Mojang/Forge metadata that isn't what we expect.
    #[error("unexpected metadata: {0}")]
    Metadata(String),
    #[error("could not download {key} from any repository: {source}")]
    AllMirrorsFailed {
        key: String,
        #[source]
        source: larp_net::NetError,
    },
}

impl InstallError {
    pub(crate) fn io(path: impl Into<PathBuf>, source: std::io::Error) -> Self {
        Self::Io {
            path: path.into(),
            source,
        }
    }
}
