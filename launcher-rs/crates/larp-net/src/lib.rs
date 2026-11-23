//! HTTP for the LARP launcher: small API calls, and verified, atomic, retried file downloads.
//!
//! Guarantees of [`Net::download`]:
//! - the target is only ever replaced by a file whose SHA-1 is one of the accepted hashes;
//! - the replacement is an atomic rename, so a crash never leaves a half-written target;
//! - the `.part` file is removed on every exit path, including cancellation (future dropped);
//! - network errors, 5xx, 408, 429 and checksum mismatches are retried with exponential backoff
//!   and jitter; other 4xx fail immediately.

mod download;
mod error;
mod parallel;

pub use download::sha1_file;
pub use error::NetError;
pub use parallel::run_bounded;

use std::time::Duration;

pub const USER_AGENT: &str = concat!("LARPClient-Launcher/", env!("CARGO_PKG_VERSION"));
/// Attempts per download, including the first.
pub const ATTEMPTS: u32 = 3;

/// A shared HTTP client. Cheap to clone (the connection pool is shared).
#[derive(Debug, Clone)]
pub struct Net {
    client: reqwest::Client,
    retry_base: Duration,
}

impl Net {
    pub fn new() -> Result<Self, NetError> {
        let client = reqwest::Client::builder()
            .user_agent(USER_AGENT)
            .connect_timeout(Duration::from_secs(15))
            .read_timeout(Duration::from_secs(30))
            .build()
            .map_err(|source| NetError::Request {
                url: String::new(),
                source,
            })?;
        Ok(Self {
            client,
            retry_base: Duration::from_millis(500),
        })
    }

    /// Base delay before the second attempt (doubled for each further one). Tests use zero.
    #[must_use]
    pub const fn with_retry_base(mut self, base: Duration) -> Self {
        self.retry_base = base;
        self
    }

    pub async fn get_text(&self, url: &str) -> Result<String, NetError> {
        self.send(url, self.client.get(url)).await
    }

    pub async fn get_text_bearer(&self, url: &str, bearer: &str) -> Result<String, NetError> {
        self.send(url, self.client.get(url).bearer_auth(bearer)).await
    }

    pub async fn post_json(&self, url: &str, json: &str) -> Result<String, NetError> {
        let request = self
            .client
            .post(url)
            .header(reqwest::header::CONTENT_TYPE, "application/json")
            .header(reqwest::header::ACCEPT, "application/json")
            .body(json.to_owned());
        self.send(url, request).await
    }

    pub async fn post_form(&self, url: &str, form: &[(&str, &str)]) -> Result<String, NetError> {
        self.send(url, self.client.post(url).form(form)).await
    }

    async fn send(&self, url: &str, request: reqwest::RequestBuilder) -> Result<String, NetError> {
        let request_error = |source| NetError::Request {
            url: url.to_owned(),
            source,
        };
        let response = request
            .timeout(Duration::from_secs(30))
            .send()
            .await
            .map_err(request_error)?;
        let status = response.status();
        let body = response.text().await.map_err(request_error)?;
        if !status.is_success() {
            return Err(NetError::Status {
                status: status.as_u16(),
                url: url.to_owned(),
                body,
            });
        }
        Ok(body)
    }
}

/// Delay before attempt `attempt` (2 = first retry): `base · 2^(attempt-2)` plus up to 50 % jitter.
pub fn backoff(attempt: u32, base: Duration, jitter: f64) -> Duration {
    let exp = attempt.saturating_sub(2).min(16);
    let delay = base.saturating_mul(1 << exp);
    delay + delay.mul_f64(jitter.clamp(0.0, 1.0) * 0.5)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn backoff_doubles_with_bounded_jitter() {
        let base = Duration::from_millis(500);
        assert_eq!(backoff(2, base, 0.0), Duration::from_millis(500));
        assert_eq!(backoff(3, base, 0.0), Duration::from_millis(1000));
        assert_eq!(backoff(3, base, 1.0), Duration::from_millis(1500));
        assert_eq!(backoff(3, base, 7.0), Duration::from_millis(1500), "jitter is clamped");
    }
}
