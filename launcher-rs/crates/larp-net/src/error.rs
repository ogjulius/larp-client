use std::path::PathBuf;

#[derive(Debug, thiserror::Error)]
pub enum NetError {
    #[error("HTTP {status} for {url}")]
    Status { status: u16, url: String, body: String },
    #[error("request to {url} failed: {source}")]
    Request {
        url: String,
        #[source]
        source: reqwest::Error,
    },
    #[error("checksum mismatch for {url} (got {actual})")]
    Checksum { url: String, actual: String },
    #[error("{path}: {source}")]
    Io {
        path: PathBuf,
        #[source]
        source: std::io::Error,
    },
}

impl NetError {
    /// HTTP status, if the server answered with an error status.
    pub const fn status(&self) -> Option<u16> {
        match self {
            Self::Status { status, .. } => Some(*status),
            _ => None,
        }
    }

    /// Whether trying again could help: network errors, 5xx, 408, 429 and corrupted transfers.
    pub const fn is_retryable(&self) -> bool {
        match self {
            Self::Status { status, .. } => *status >= 500 || *status == 408 || *status == 429,
            Self::Request { .. } | Self::Checksum { .. } => true,
            Self::Io { .. } => false,
        }
    }

    pub(crate) fn io(path: impl Into<PathBuf>, source: std::io::Error) -> Self {
        Self::Io {
            path: path.into(),
            source,
        }
    }
}
