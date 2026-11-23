use crate::{ATTEMPTS, Net, NetError, backoff};
use futures::StreamExt;
use sha1::{Digest, Sha1};
use std::io::Read;
use std::path::{Path, PathBuf};
use tokio::io::AsyncWriteExt;

/// Deletes the `.part` file when dropped: on errors and when the download future is cancelled.
struct PartFile(PathBuf);

impl Drop for PartFile {
    fn drop(&mut self) {
        let _ = std::fs::remove_file(&self.0); // usually already renamed away; nothing to report
    }
}

fn part_path(target: &Path) -> PathBuf {
    let mut name = target.file_name().map(|n| n.to_os_string()).unwrap_or_default();
    name.push(".part");
    target.with_file_name(name)
}

fn accepted_contains(accepted: &[String], actual: &str) -> bool {
    accepted.is_empty() || accepted.iter().any(|a| a.eq_ignore_ascii_case(actual))
}

/// Hex SHA-1 of a file, computed off the async executor.
pub async fn sha1_file(path: &Path) -> std::io::Result<String> {
    let path = path.to_path_buf();
    tokio::task::spawn_blocking(move || {
        let mut file = std::fs::File::open(&path)?;
        let mut hasher = Sha1::new();
        let mut buf = vec![0u8; 64 * 1024];
        loop {
            let n = file.read(&mut buf)?;
            if n == 0 {
                break;
            }
            hasher.update(&buf[..n]);
        }
        Ok(hex::encode(hasher.finalize()))
    })
    .await
    .map_err(std::io::Error::other)?
}

impl Net {
    /// Download `url` to `target` unless it already exists and matches one of `accepted`
    /// (empty `accepted`: no verification, an existing file is kept). Returns whether a download happened.
    pub async fn download(&self, url: &str, target: &Path, accepted: &[String]) -> Result<bool, NetError> {
        if tokio::fs::metadata(target).await.is_ok_and(|m| m.is_file()) {
            if accepted.is_empty() {
                return Ok(false);
            }
            let actual = sha1_file(target).await.map_err(|e| NetError::io(target, e))?;
            if accepted_contains(accepted, &actual) {
                return Ok(false);
            }
        }
        if let Some(parent) = target.parent() {
            tokio::fs::create_dir_all(parent)
                .await
                .map_err(|e| NetError::io(parent, e))?;
        }
        let part = PartFile(part_path(target));
        let mut attempt = 1;
        loop {
            match self.fetch(url, &part.0, accepted).await {
                Ok(()) => {
                    tokio::fs::rename(&part.0, target)
                        .await
                        .map_err(|e| NetError::io(target, e))?;
                    return Ok(true);
                }
                Err(e) if e.is_retryable() && attempt < ATTEMPTS => {
                    attempt += 1;
                    tokio::time::sleep(backoff(attempt, self.retry_base, fastrand::f64())).await;
                }
                Err(e) => return Err(e),
            }
        }
    }

    /// One attempt: stream into `part` while hashing, then verify.
    async fn fetch(&self, url: &str, part: &Path, accepted: &[String]) -> Result<(), NetError> {
        let request_error = |source| NetError::Request {
            url: url.to_owned(),
            source,
        };
        let response = self
            .client
            .get(url)
            .timeout(std::time::Duration::from_secs(300))
            .send()
            .await
            .map_err(request_error)?;
        if response.status() != reqwest::StatusCode::OK {
            return Err(NetError::Status {
                status: response.status().as_u16(),
                url: url.to_owned(),
                body: String::new(),
            });
        }
        let mut file = tokio::fs::File::create(part).await.map_err(|e| NetError::io(part, e))?;
        let mut hasher = Sha1::new();
        let mut body = response.bytes_stream();
        while let Some(chunk) = body.next().await {
            let chunk = chunk.map_err(request_error)?;
            hasher.update(&chunk);
            file.write_all(&chunk).await.map_err(|e| NetError::io(part, e))?;
        }
        file.sync_all().await.map_err(|e| NetError::io(part, e))?;
        let actual = hex::encode(hasher.finalize());
        if !accepted_contains(accepted, &actual) {
            return Err(NetError::Checksum {
                url: url.to_owned(),
                actual,
            });
        }
        Ok(())
    }
}
