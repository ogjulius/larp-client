#![allow(clippy::unwrap_used, clippy::expect_used, clippy::panic)] // test code: failing loudly is the point

use larp_net::{Net, NetError};
use sha1::{Digest, Sha1};
use std::time::Duration;
use wiremock::matchers::{method, path};
use wiremock::{Mock, MockServer, ResponseTemplate};

const BODY: &[u8] = b"hello minecraft";

fn sha1(data: &[u8]) -> String {
    hex::encode(Sha1::digest(data))
}

fn net() -> Net {
    Net::new().unwrap().with_retry_base(Duration::ZERO)
}

async fn serve(server: &MockServer, statuses: &[u16]) {
    for &status in statuses {
        Mock::given(method("GET"))
            .and(path("/file.jar"))
            .respond_with(ResponseTemplate::new(status))
            .up_to_n_times(1)
            .mount(server)
            .await;
    }
    Mock::given(method("GET"))
        .and(path("/file.jar"))
        .respond_with(ResponseTemplate::new(200).set_body_bytes(BODY))
        .mount(server)
        .await;
}

async fn requests(server: &MockServer) -> usize {
    server.received_requests().await.unwrap_or_default().len()
}

#[tokio::test]
async fn downloads_verifies_and_skips_when_present() {
    let server = MockServer::start().await;
    serve(&server, &[]).await;
    let dir = tempfile::tempdir().unwrap();
    let target = dir.path().join("a/file.jar");
    let url = format!("{}/file.jar", server.uri());
    assert!(net().download(&url, &target, &[sha1(BODY)]).await.unwrap());
    assert_eq!(std::fs::read(&target).unwrap(), BODY);
    assert!(!net().download(&url, &target, &[sha1(BODY)]).await.unwrap());
    assert_eq!(requests(&server).await, 1);
}

#[tokio::test]
async fn any_of_several_checksums_is_accepted() {
    let server = MockServer::start().await;
    serve(&server, &[]).await;
    let dir = tempfile::tempdir().unwrap();
    let accepted = ["0".repeat(40), sha1(BODY).to_uppercase()];
    assert!(
        net()
            .download(&format!("{}/file.jar", server.uri()), &dir.path().join("f"), &accepted)
            .await
            .unwrap()
    );
}

#[tokio::test]
async fn replaces_existing_file_with_wrong_hash() {
    let server = MockServer::start().await;
    serve(&server, &[]).await;
    let dir = tempfile::tempdir().unwrap();
    let target = dir.path().join("file.jar");
    std::fs::write(&target, "tampered").unwrap();
    assert!(
        net()
            .download(&format!("{}/file.jar", server.uri()), &target, &[sha1(BODY)])
            .await
            .unwrap()
    );
    assert_eq!(std::fs::read(&target).unwrap(), BODY);
}

#[tokio::test]
async fn checksum_mismatch_never_replaces_target_or_leaves_part_file() {
    let server = MockServer::start().await;
    serve(&server, &[]).await;
    let dir = tempfile::tempdir().unwrap();
    let target = dir.path().join("file.jar");
    std::fs::write(&target, "old").unwrap();
    let err = net()
        .download(&format!("{}/file.jar", server.uri()), &target, &[sha1(b"other")])
        .await
        .unwrap_err();
    assert!(matches!(err, NetError::Checksum { .. }), "{err}");
    assert_eq!(std::fs::read_to_string(&target).unwrap(), "old");
    assert!(!dir.path().join("file.jar.part").exists());
    assert_eq!(requests(&server).await, 3, "a corrupted transfer is retried");
}

#[tokio::test]
async fn client_errors_are_not_retried() {
    for status in [403, 404] {
        let server = MockServer::start().await;
        serve(&server, &[status]).await;
        let dir = tempfile::tempdir().unwrap();
        let err = net()
            .download(&format!("{}/file.jar", server.uri()), &dir.path().join("f"), &[])
            .await
            .unwrap_err();
        assert_eq!(err.status(), Some(status));
        assert_eq!(requests(&server).await, 1);
    }
}

#[tokio::test]
async fn server_errors_are_retried_then_succeed() {
    let server = MockServer::start().await;
    serve(&server, &[503, 500]).await;
    let dir = tempfile::tempdir().unwrap();
    assert!(
        net()
            .download(
                &format!("{}/file.jar", server.uri()),
                &dir.path().join("f"),
                &[sha1(BODY)]
            )
            .await
            .unwrap()
    );
    assert_eq!(requests(&server).await, 3);
}

#[tokio::test]
async fn gives_up_after_three_attempts_without_leftovers() {
    let server = MockServer::start().await;
    serve(&server, &[503, 503, 503]).await;
    let dir = tempfile::tempdir().unwrap();
    let err = net()
        .download(&format!("{}/file.jar", server.uri()), &dir.path().join("f"), &[])
        .await
        .unwrap_err();
    assert_eq!(err.status(), Some(503));
    assert_eq!(requests(&server).await, 3);
    assert_eq!(std::fs::read_dir(dir.path()).unwrap().count(), 0);
}

/// Cancelling a download mid-body (e.g. a sibling download failed) must not leave a `.part` file behind.
#[tokio::test]
async fn cancelled_download_leaves_no_part_file() {
    use tokio::io::{AsyncReadExt, AsyncWriteExt};
    // A server that sends the headers and half the body, then stalls.
    let listener = tokio::net::TcpListener::bind("127.0.0.1:0").await.unwrap();
    let addr = listener.local_addr().unwrap();
    tokio::spawn(async move {
        let (mut socket, _) = listener.accept().await.unwrap();
        let mut request = [0u8; 1024];
        let _ = socket.read(&mut request).await;
        socket
            .write_all(b"HTTP/1.1 200 OK\r\nContent-Length: 2000\r\n\r\n")
            .await
            .unwrap();
        socket.write_all(&[7u8; 1000]).await.unwrap();
        socket.flush().await.unwrap();
        tokio::time::sleep(Duration::from_secs(60)).await;
    });
    let dir = tempfile::tempdir().unwrap();
    let target = dir.path().join("slow.jar");
    let part = dir.path().join("slow.jar.part");
    let net = net();
    let url = format!("http://{addr}/slow.jar");
    let mut download = Box::pin(net.download(&url, &target, &[])); // owned, so drop() really cancels it
    // Run until the partial body has been written, then cancel by dropping the future.
    let deadline = tokio::time::Instant::now() + Duration::from_secs(5);
    while !part.exists() {
        assert!(tokio::time::Instant::now() < deadline, "no .part file appeared");
        let _ = tokio::time::timeout(Duration::from_millis(10), &mut download).await;
    }
    drop(download); // cancel
    assert!(!part.exists(), ".part file left behind after cancellation");
    assert!(!target.exists());
}

#[tokio::test]
async fn api_calls_report_status_and_body() {
    let server = MockServer::start().await;
    Mock::given(method("GET"))
        .and(path("/manifest.json"))
        .respond_with(ResponseTemplate::new(404).set_body_string(r#"{"error":"not found"}"#))
        .mount(&server)
        .await;
    let err = net()
        .get_text(&format!("{}/manifest.json", server.uri()))
        .await
        .unwrap_err();
    match err {
        NetError::Status { status, body, .. } => {
            assert_eq!(status, 404);
            assert!(body.contains("not found"));
        }
        other => panic!("unexpected {other}"),
    }
}
