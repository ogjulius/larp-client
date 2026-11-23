//! The sign-in flow against scripted responses (mirrors the Java launcher's MicrosoftAuthTest).

use super::*;
use std::collections::{HashMap, VecDeque};
use std::sync::Mutex;

#[derive(Default)]
struct Fake {
    responses: Mutex<HashMap<&'static str, VecDeque<(u16, String)>>>,
    requests: Mutex<Vec<String>>,
    sleeps: Mutex<Vec<u64>>,
}

impl Fake {
    fn on(self, url: &'static str, status: u16, body: &str) -> Self {
        self.responses
            .lock()
            .unwrap()
            .entry(url)
            .or_default()
            .push_back((status, body.to_owned()));
        self
    }

    fn answer(&self, url: &str, request: &str) -> Result<String, NetError> {
        self.requests.lock().unwrap().push(format!("{url} {request}"));
        let (status, body) = self
            .responses
            .lock()
            .unwrap()
            .get_mut(url)
            .and_then(VecDeque::pop_front)
            .unwrap_or_else(|| panic!("unexpected request to {url}"));
        if (200..300).contains(&status) {
            Ok(body)
        } else {
            Err(NetError::Status {
                status,
                url: url.into(),
                body,
            })
        }
    }

    fn happy_xbox(self) -> Self {
        self.on(
            XBL,
            200,
            r#"{"Token":"xbl-token","DisplayClaims":{"xui":[{"uhs":"user-hash"}]}}"#,
        )
        .on(XSTS, 200, r#"{"Token":"xsts-token"}"#)
        .on(MC_LOGIN, 200, r#"{"access_token":"mc-token","expires_in":86400}"#)
        .on(
            MC_PROFILE,
            200,
            r#"{"id":"0123456789abcdef0123456789abcdef","name":"Steve"}"#,
        )
    }

    fn device_code(self, expires_in: u64) -> Self {
        let body = format!(
            r#"{{"user_code":"ABCD","verification_uri":"https://microsoft.com/link","device_code":"dev","interval":1,"expires_in":{expires_in}}}"#
        );
        self.on(DEVICE_CODE, 200, &body)
    }
}

impl Transport for &Fake {
    async fn post_form(&self, url: &str, form: &[(&str, &str)]) -> Result<String, NetError> {
        self.answer(url, &format!("{form:?}"))
    }
    async fn post_json(&self, url: &str, json: &str) -> Result<String, NetError> {
        self.answer(url, json)
    }
    async fn get_bearer(&self, url: &str, bearer: &str) -> Result<String, NetError> {
        self.answer(url, &format!("Bearer {bearer}"))
    }
    async fn sleep(&self, duration: Duration) {
        self.sleeps.lock().unwrap().push(duration.as_secs());
    }
}

fn auth(fake: &Fake) -> MicrosoftAuth<&Fake> {
    MicrosoftAuth::new("client-id", fake).unwrap()
}

fn account(refresh: &str) -> Account {
    Account {
        kind: AccountKind::Microsoft,
        username: String::new(),
        uuid: String::new(),
        access_token: Secret::default(),
        access_token_expires_at: 0,
        refresh_token: Some(Secret::new(refresh)),
    }
}

#[tokio::test]
async fn device_code_flow_handles_pending_and_slow_down_then_signs_in() {
    let fake = Fake::default()
        .device_code(900)
        .on(TOKEN, 400, r#"{"error":"authorization_pending"}"#)
        .on(TOKEN, 400, r#"{"error":"slow_down"}"#)
        .on(TOKEN, 200, r#"{"access_token":"ms-token","refresh_token":"refresh-1"}"#)
        .happy_xbox();
    let auth = auth(&fake);
    let code = auth.request_device_code().await.unwrap();
    assert_eq!(code.user_code, "ABCD");

    let a = auth.await_login(&code, || false).await.unwrap();

    assert_eq!(a.kind, AccountKind::Microsoft);
    assert_eq!(a.username, "Steve");
    assert_eq!(a.uuid, "0123456789abcdef0123456789abcdef");
    assert_eq!(a.access_token.expose(), "mc-token");
    assert_eq!(a.refresh_token.unwrap().expose(), "refresh-1");
    assert!(a.access_token_expires_at > now_millis());
    assert_eq!(
        *fake.sleeps.lock().unwrap(),
        [1, 1, 6],
        "slow_down adds 5 s to the interval"
    );
    let requests = fake.requests.lock().unwrap();
    assert!(
        requests
            .iter()
            .any(|r| r.starts_with(MC_LOGIN) && r.contains("XBL3.0 x=user-hash;xsts-token"))
    );
    assert!(requests.iter().any(|r| r.starts_with(XBL) && r.contains("d=ms-token")));
    assert!(
        requests
            .iter()
            .any(|r| r.starts_with(MC_PROFILE) && r.contains("Bearer mc-token"))
    );
}

#[tokio::test]
async fn declined_cancelled_and_expired_are_reported() {
    let fake = Fake::default()
        .device_code(900)
        .on(TOKEN, 400, r#"{"error":"authorization_declined"}"#);
    let a = auth(&fake);
    let code = a.request_device_code().await.unwrap();
    assert!(matches!(a.await_login(&code, || false).await, Err(AuthError::Declined)));

    let fake = Fake::default().device_code(900);
    let a = auth(&fake);
    let code = a.request_device_code().await.unwrap();
    assert!(matches!(a.await_login(&code, || true).await, Err(AuthError::Cancelled)));

    let fake = Fake::default().device_code(0);
    let a = auth(&fake);
    let code = a.request_device_code().await.unwrap();
    assert!(matches!(
        a.await_login(&code, || false).await,
        Err(AuthError::CodeExpired)
    ));
}

#[tokio::test]
async fn xsts_error_codes_become_readable_messages() {
    let fake = Fake::default()
        .on(TOKEN, 200, r#"{"access_token":"ms","refresh_token":"r"}"#)
        .on(XBL, 200, r#"{"Token":"x","DisplayClaims":{"xui":[{"uhs":"h"}]}}"#)
        .on(XSTS, 401, r#"{"XErr":2148916233}"#);
    let err = auth(&fake).refresh(&mut account("r")).await.unwrap_err();
    assert!(err.to_string().contains("no Xbox profile"), "{err}");
}

#[tokio::test]
async fn unapproved_app_and_missing_game_are_explained() {
    let fake = Fake::default()
        .on(TOKEN, 200, r#"{"access_token":"ms"}"#)
        .on(XBL, 200, r#"{"Token":"x","DisplayClaims":{"xui":[{"uhs":"h"}]}}"#)
        .on(XSTS, 200, r#"{"Token":"s"}"#)
        .on(MC_LOGIN, 403, "");
    assert!(matches!(
        auth(&fake).refresh(&mut account("r")).await,
        Err(AuthError::AppNotApproved)
    ));

    let fake = Fake::default()
        .on(TOKEN, 200, r#"{"access_token":"ms"}"#)
        .on(XBL, 200, r#"{"Token":"x","DisplayClaims":{"xui":[{"uhs":"h"}]}}"#)
        .on(XSTS, 200, r#"{"Token":"s"}"#)
        .on(MC_LOGIN, 200, r#"{"access_token":"mc"}"#)
        .on(MC_PROFILE, 404, "{}");
    assert!(matches!(
        auth(&fake).refresh(&mut account("r")).await,
        Err(AuthError::NoGame)
    ));
}

#[tokio::test]
async fn refresh_updates_tokens() {
    let fake = Fake::default()
        .on(TOKEN, 200, r#"{"access_token":"ms","refresh_token":"refresh-2"}"#)
        .happy_xbox();
    let mut a = account("refresh-1");
    auth(&fake).refresh(&mut a).await.unwrap();
    assert_eq!(a.access_token.expose(), "mc-token");
    assert_eq!(a.refresh_token.unwrap().expose(), "refresh-2");
}

/// Audit A16: a refresh response without a new refresh token keeps the old one.
#[tokio::test]
async fn refresh_without_new_refresh_token_keeps_the_old_one() {
    let fake = Fake::default().on(TOKEN, 200, r#"{"access_token":"ms"}"#).happy_xbox();
    let mut a = account("refresh-1");
    auth(&fake).refresh(&mut a).await.unwrap();
    assert_eq!(a.refresh_token.unwrap().expose(), "refresh-1");
}

#[tokio::test]
async fn missing_refresh_token_asks_to_sign_in_again() {
    let fake = Fake::default();
    let mut a = account("");
    assert!(matches!(
        auth(&fake).refresh(&mut a).await,
        Err(AuthError::SignInExpired)
    ));
    assert!(fake.requests.lock().unwrap().is_empty());
}

/// Audit A17: malformed responses fail with a clear message, never a crash.
#[tokio::test]
async fn malformed_responses_fail_cleanly() {
    let fake = Fake::default().on(TOKEN, 200, r#"{"access_token":"ms"}"#).on(
        XBL,
        200,
        r#"{"Token":"x","DisplayClaims":{"xui":[]}}"#,
    );
    let err = auth(&fake).refresh(&mut account("r")).await.unwrap_err();
    assert_eq!(err.to_string(), "Unexpected response from Xbox Live: missing user hash");

    let fake = Fake::default()
        .device_code(900)
        .on(TOKEN, 502, "<html>Bad gateway</html>");
    let a = auth(&fake);
    let code = a.request_device_code().await.unwrap();
    assert_eq!(
        a.await_login(&code, || false).await.unwrap_err().to_string(),
        "Microsoft sign-in failed (HTTP 502)"
    );

    let fake = Fake::default().on(TOKEN, 200, "{}");
    let err = auth(&fake).refresh(&mut account("r")).await.unwrap_err();
    assert_eq!(
        err.to_string(),
        "Unexpected response from Microsoft: missing access_token"
    );

    let fake = Fake::default().on(TOKEN, 200, "<html>");
    assert!(matches!(
        auth(&fake).refresh(&mut account("r")).await,
        Err(AuthError::Unexpected { .. })
    ));
}

#[test]
fn blank_client_id_is_rejected() {
    let fake = Fake::default();
    assert!(matches!(MicrosoftAuth::new("  ", &fake), Err(AuthError::NoClientId)));
}
