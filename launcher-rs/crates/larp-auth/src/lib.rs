//! Microsoft → Xbox Live → XSTS → Minecraft sign-in with the OAuth device-code flow (no embedded
//! browser or local web server). Needs an Azure application ID approved for the Minecraft API.

use larp_core::{Account, AccountKind, Secret};
use larp_net::{Net, NetError};
use serde_json::{Value, json};
use std::future::Future;
use std::time::{Duration, Instant, SystemTime, UNIX_EPOCH};

const DEVICE_CODE: &str = "https://login.microsoftonline.com/consumers/oauth2/v2.0/devicecode";
const TOKEN: &str = "https://login.microsoftonline.com/consumers/oauth2/v2.0/token";
const XBL: &str = "https://user.auth.xboxlive.com/user/authenticate";
const XSTS: &str = "https://xsts.auth.xboxlive.com/xsts/authorize";
const MC_LOGIN: &str = "https://api.minecraftservices.com/authentication/login_with_xbox";
const MC_PROFILE: &str = "https://api.minecraftservices.com/minecraft/profile";
const SCOPE: &str = "XboxLive.signin offline_access";

#[derive(Debug, thiserror::Error)]
pub enum AuthError {
    #[error("No Microsoft client ID configured (Settings → Microsoft sign-in)")]
    NoClientId,
    #[error("Sign-in cancelled")]
    Cancelled,
    #[error("The sign-in code expired, please try again")]
    CodeExpired,
    #[error("Sign-in was declined")]
    Declined,
    #[error("Invalid sign-in code")]
    BadCode,
    #[error("Microsoft sign-in failed: {0}")]
    OAuth(String),
    #[error("Microsoft sign-in failed (HTTP {0})")]
    OAuthHttp(u16),
    #[error("{0}")]
    Xsts(String),
    #[error("This Azure app is not approved for Minecraft sign-in yet (see DEVELOPMENT.md)")]
    AppNotApproved,
    #[error("This Microsoft account does not own Minecraft Java Edition")]
    NoGame,
    #[error("This account's sign-in has expired. Remove it and sign in again.")]
    SignInExpired,
    #[error("Unexpected response from {service}: {detail}")]
    Unexpected { service: &'static str, detail: String },
    #[error(transparent)]
    Net(#[from] NetError),
}

/// The HTTP calls and waits the flow needs; [`Net`] in production, scripted in tests.
pub trait Transport: Sync {
    fn post_form(&self, url: &str, form: &[(&str, &str)]) -> impl Future<Output = Result<String, NetError>> + Send;
    fn post_json(&self, url: &str, json: &str) -> impl Future<Output = Result<String, NetError>> + Send;
    fn get_bearer(&self, url: &str, bearer: &str) -> impl Future<Output = Result<String, NetError>> + Send;
    fn sleep(&self, duration: Duration) -> impl Future<Output = ()> + Send;
}

impl Transport for Net {
    async fn post_form(&self, url: &str, form: &[(&str, &str)]) -> Result<String, NetError> {
        Net::post_form(self, url, form).await
    }
    async fn post_json(&self, url: &str, json: &str) -> Result<String, NetError> {
        Net::post_json(self, url, json).await
    }
    async fn get_bearer(&self, url: &str, bearer: &str) -> Result<String, NetError> {
        self.get_text_bearer(url, bearer).await
    }
    async fn sleep(&self, duration: Duration) {
        tokio::time::sleep(duration).await;
    }
}

/// Shown to the user: open `verification_uri` and enter `user_code`.
#[derive(Debug, Clone)]
pub struct DeviceCode {
    pub user_code: String,
    pub verification_uri: String,
    device_code: Secret,
    interval: Duration,
    expires_at: Instant,
}

#[derive(Debug)]
pub struct MicrosoftAuth<T> {
    client_id: String,
    http: T,
}

fn now_millis() -> i64 {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map_or(0, |d| i64::try_from(d.as_millis()).unwrap_or(i64::MAX))
}

/// Parse a JSON object, turning anything else (HTML error pages, garbage) into a clear error.
fn parse(body: &str, service: &'static str) -> Result<Value, AuthError> {
    match serde_json::from_str::<Value>(body) {
        Ok(v) if v.is_object() => Ok(v),
        Ok(_) => Err(AuthError::Unexpected {
            service,
            detail: "not a JSON object".into(),
        }),
        Err(_) => Err(AuthError::Unexpected {
            service,
            detail: "not JSON".into(),
        }),
    }
}

fn required(v: &Value, key: &str, service: &'static str) -> Result<String, AuthError> {
    v.get(key)
        .and_then(Value::as_str)
        .filter(|s| !s.is_empty())
        .map(str::to_owned)
        .ok_or_else(|| AuthError::Unexpected {
            service,
            detail: format!("missing {key}"),
        })
}

/// The OAuth `error` code from an error body, or "" if there is none (e.g. an HTML page).
fn oauth_error(body: &str) -> String {
    serde_json::from_str::<Value>(body)
        .ok()
        .and_then(|v| v.get("error").and_then(Value::as_str).map(str::to_owned))
        .unwrap_or_default()
}

fn describe_xsts(body: &str) -> String {
    let code = serde_json::from_str::<Value>(body)
        .ok()
        .and_then(|v| v.get("XErr").and_then(Value::as_u64))
        .unwrap_or(0);
    match code {
        2_148_916_233 => "This Microsoft account has no Xbox profile. Sign in at minecraft.net once first.".into(),
        2_148_916_238 => "Child accounts must be added to a Microsoft family by an adult first.".into(),
        2_148_916_235 => "Xbox Live is not available in your country.".into(),
        0 => "Xbox Live sign-in failed".into(),
        code => format!("Xbox Live sign-in failed ({code})"),
    }
}

impl<T: Transport> MicrosoftAuth<T> {
    pub fn new(client_id: &str, http: T) -> Result<Self, AuthError> {
        let client_id = client_id.trim();
        if client_id.is_empty() {
            return Err(AuthError::NoClientId);
        }
        Ok(Self {
            client_id: client_id.to_owned(),
            http,
        })
    }

    pub async fn request_device_code(&self) -> Result<DeviceCode, AuthError> {
        let body = self
            .http
            .post_form(DEVICE_CODE, &[("client_id", &self.client_id), ("scope", SCOPE)])
            .await?;
        let v = parse(&body, "Microsoft")?;
        let seconds = |key: &str, default: u64| v.get(key).and_then(Value::as_u64).unwrap_or(default);
        Ok(DeviceCode {
            user_code: required(&v, "user_code", "Microsoft")?,
            verification_uri: required(&v, "verification_uri", "Microsoft")?,
            device_code: Secret::new(required(&v, "device_code", "Microsoft")?),
            interval: Duration::from_secs(seconds("interval", 5)),
            expires_at: Instant::now() + Duration::from_secs(seconds("expires_in", 900)),
        })
    }

    /// Poll until the user finishes signing in, then complete the Minecraft login.
    pub async fn await_login(&self, code: &DeviceCode, cancelled: impl Fn() -> bool) -> Result<Account, AuthError> {
        let mut interval = code.interval;
        while Instant::now() < code.expires_at {
            if cancelled() {
                return Err(AuthError::Cancelled);
            }
            self.http.sleep(interval).await;
            let form = [
                ("grant_type", "urn:ietf:params:oauth:grant-type:device_code"),
                ("client_id", self.client_id.as_str()),
                ("device_code", code.device_code.expose()),
            ];
            match self.http.post_form(TOKEN, &form).await {
                Ok(body) => return self.complete_login(&parse(&body, "Microsoft")?).await,
                Err(NetError::Status { status, body, .. }) => match oauth_error(&body).as_str() {
                    "authorization_pending" => {}
                    "slow_down" => interval += Duration::from_secs(5),
                    "expired_token" => return Err(AuthError::CodeExpired),
                    "authorization_declined" => return Err(AuthError::Declined),
                    "bad_verification_code" => return Err(AuthError::BadCode),
                    "" => return Err(AuthError::OAuthHttp(status)),
                    other => return Err(AuthError::OAuth(other.to_owned())),
                },
                Err(e) => return Err(e.into()),
            }
        }
        Err(AuthError::CodeExpired)
    }

    /// Use the stored refresh token to get a fresh Minecraft access token.
    pub async fn refresh(&self, account: &mut Account) -> Result<(), AuthError> {
        let refresh = account
            .refresh_token
            .as_ref()
            .filter(|t| !t.is_empty())
            .ok_or(AuthError::SignInExpired)?;
        let form = [
            ("grant_type", "refresh_token"),
            ("client_id", self.client_id.as_str()),
            ("refresh_token", refresh.expose()),
            ("scope", SCOPE),
        ];
        let token = parse(&self.http.post_form(TOKEN, &form).await?, "Microsoft")?;
        let fresh = self.complete_login(&token).await?;
        account.username = fresh.username;
        account.uuid = fresh.uuid;
        account.access_token = fresh.access_token;
        account.access_token_expires_at = fresh.access_token_expires_at;
        if fresh.refresh_token.is_some() {
            account.refresh_token = fresh.refresh_token; // OAuth may omit it: the old one stays valid
        }
        Ok(())
    }

    async fn complete_login(&self, ms_token: &Value) -> Result<Account, AuthError> {
        let ms_access = required(ms_token, "access_token", "Microsoft")?;

        let xbl_request = json!({
            "Properties": {"AuthMethod": "RPS", "SiteName": "user.auth.xboxlive.com", "RpsTicket": format!("d={ms_access}")},
            "RelyingParty": "http://auth.xboxlive.com",
            "TokenType": "JWT"
        });
        let xbl = parse(&self.http.post_json(XBL, &xbl_request.to_string()).await?, "Xbox Live")?;
        let xbl_token = required(&xbl, "Token", "Xbox Live")?;
        let user_hash = xbl
            .pointer("/DisplayClaims/xui/0/uhs")
            .and_then(Value::as_str)
            .map(str::to_owned)
            .ok_or(AuthError::Unexpected {
                service: "Xbox Live",
                detail: "missing user hash".into(),
            })?;

        let xsts_request = json!({
            "Properties": {"SandboxId": "RETAIL", "UserTokens": [xbl_token]},
            "RelyingParty": "rp://api.minecraftservices.com/",
            "TokenType": "JWT"
        });
        let xsts_token = match self.http.post_json(XSTS, &xsts_request.to_string()).await {
            Ok(body) => required(&parse(&body, "Xbox Live")?, "Token", "Xbox Live")?,
            Err(NetError::Status { body, .. }) => return Err(AuthError::Xsts(describe_xsts(&body))),
            Err(e) => return Err(e.into()),
        };

        let login_request = json!({"identityToken": format!("XBL3.0 x={user_hash};{xsts_token}")});
        let login = match self.http.post_json(MC_LOGIN, &login_request.to_string()).await {
            Ok(body) => parse(&body, "Minecraft")?,
            Err(NetError::Status { status: 403, .. }) => return Err(AuthError::AppNotApproved),
            Err(e) => return Err(e.into()),
        };
        let mc_access = required(&login, "access_token", "Minecraft")?;

        let profile = match self.http.get_bearer(MC_PROFILE, &mc_access).await {
            Ok(body) => parse(&body, "Minecraft")?,
            Err(NetError::Status { status: 404, .. }) => return Err(AuthError::NoGame),
            Err(e) => return Err(e.into()),
        };
        let expires_in = login.get("expires_in").and_then(Value::as_i64).unwrap_or(86_400);
        Ok(Account {
            kind: AccountKind::Microsoft,
            username: required(&profile, "name", "Minecraft")?,
            uuid: required(&profile, "id", "Minecraft")?,
            access_token: Secret::new(mc_access),
            access_token_expires_at: now_millis().saturating_add(expires_in.saturating_mul(1000)),
            refresh_token: ms_token.get("refresh_token").and_then(Value::as_str).map(Secret::new),
        })
    }
}

#[cfg(test)]
mod tests;
