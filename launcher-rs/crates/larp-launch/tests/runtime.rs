#![allow(clippy::unwrap_used, clippy::expect_used, clippy::panic)] // test code: failing loudly is the point
#![cfg(unix)]

//! Mojang `jre-legacy` download against a local server.

use larp_core::{GameFiles, Os};
use larp_launch::java_runtime::JavaRuntime;
use larp_net::Net;
use serde_json::json;
use sha1::{Digest, Sha1};
use std::os::unix::fs::PermissionsExt;
use std::time::Duration;
use wiremock::matchers::{method, path};
use wiremock::{Mock, MockServer, ResponseTemplate};

async fn serve(server: &MockServer, p: &str, body: Vec<u8>) {
    Mock::given(method("GET"))
        .and(path(p))
        .respond_with(ResponseTemplate::new(200).set_body_bytes(body))
        .mount(server)
        .await;
}

#[tokio::test]
async fn downloads_runtime_safely() {
    let server = MockServer::start().await;
    let base = server.uri();
    let java = b"#!/bin/sh\necho 'openjdk version \"1.8.0_51\"' >&2\n".to_vec();
    let lib = b"libjvm".to_vec();
    let sha = |d: &[u8]| hex::encode(Sha1::digest(d));
    let manifest = json!({"files": {
        "bin": {"type": "directory"},
        "bin/java": {"type": "file", "executable": true, "downloads": {"raw": {"url": format!("{base}/java"), "sha1": sha(&java)}}},
        "lib/libjvm.so": {"type": "file", "downloads": {"raw": {"url": format!("{base}/lib"), "sha1": sha(&lib)}}},
        "lib/current": {"type": "link", "target": "libjvm.so"},
        "lib/escape": {"type": "link", "target": "../../../../etc/passwd"},
        "../outside": {"type": "file", "downloads": {"raw": {"url": format!("{base}/lib"), "sha1": sha(&lib)}}}
    }});
    let platform = Os::Linux.runtime_platform(true);
    let index = json!({platform: {"jre-legacy": [{"manifest": {"url": format!("{base}/manifest.json")}}]}});
    serve(&server, "/index.json", serde_json::to_vec(&index).unwrap()).await;
    serve(&server, "/manifest.json", serde_json::to_vec(&manifest).unwrap()).await;
    serve(&server, "/java", java).await;
    serve(&server, "/lib", lib).await;

    let dir = tempfile::tempdir().unwrap();
    let files = GameFiles::new(dir.path().join("data"));
    let runtime = JavaRuntime {
        net: Net::new().unwrap().with_retry_base(Duration::ZERO),
        files: files.clone(),
        os: Os::Linux,
        runtime_index: format!("{base}/index.json"),
    };
    // resolve() would pick a system Java 8 first on a developer machine, so test the download directly.
    let java_path = runtime
        .download_mojang_runtime(&|_: &str, _: Option<f64>| {})
        .await
        .unwrap();

    let home = files.runtimes.join("jre-legacy");
    assert_eq!(java_path, home.join("bin/java"));
    assert_eq!(
        std::fs::metadata(&java_path).unwrap().permissions().mode() & 0o111,
        0o111
    );
    assert_eq!(
        std::fs::read_link(home.join("lib/current")).unwrap().to_str(),
        Some("libjvm.so")
    );
    assert!(
        home.join("lib/escape").symlink_metadata().is_err(),
        "link out of the runtime must be skipped"
    );
    assert!(!files.runtimes.join("outside").exists() && !dir.path().join("data/outside").exists());
    assert!(larp_launch::java_runtime::is_java8(&java_path, Duration::from_secs(5)).await);
}
