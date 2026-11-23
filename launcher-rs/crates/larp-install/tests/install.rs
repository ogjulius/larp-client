#![allow(clippy::unwrap_used, clippy::expect_used, clippy::panic)] // test code: failing loudly is the point

//! Full install against a local server with small synthetic Mojang/Forge metadata.

use larp_core::GameFiles;
use larp_install::{Endpoints, InstallError, Installer};
use larp_net::Net;
use serde_json::json;
use sha1::{Digest, Sha1};
use std::io::Write;
use std::path::PathBuf;
use std::time::Duration;
use wiremock::matchers::{method, path};
use wiremock::{Mock, MockServer, ResponseTemplate};

fn sha1(data: &[u8]) -> String {
    hex::encode(Sha1::digest(data))
}

fn zip(entries: &[(&str, &[u8])]) -> Vec<u8> {
    let mut out = zip::ZipWriter::new(std::io::Cursor::new(Vec::new()));
    for (name, data) in entries {
        out.start_file(*name, zip::write::SimpleFileOptions::default()).unwrap();
        out.write_all(data).unwrap();
    }
    out.finish().unwrap().into_inner()
}

struct Fixture {
    server: MockServer,
    endpoints: Endpoints,
}

async fn serve(server: &MockServer, url_path: &str, status: u16, body: Vec<u8>) {
    Mock::given(method("GET"))
        .and(path(url_path))
        .respond_with(ResponseTemplate::new(status).set_body_bytes(body))
        .mount(server)
        .await;
}

async fn fixture() -> Fixture {
    let server = MockServer::start().await;
    let base = server.uri();

    let client_jar = b"client jar".to_vec();
    let lib = b"vanilla library".to_vec();
    let natives = zip(&[
        ("liblwjgl.so", b"native code"),
        ("META-INF/MANIFEST.MF", b"skip me"),
        ("../evil.so", b"zip slip"),
    ]);
    let forge_lib = b"forge library".to_vec();
    let asset = b"asset bytes".to_vec();
    let log_config = b"<Configuration/>".to_vec();
    let index = serde_json::to_vec(&json!({"objects": {
        "minecraft/sounds/a.ogg": {"hash": sha1(&asset), "size": asset.len()},
        "minecraft/sounds/b.ogg": {"hash": sha1(&asset), "size": asset.len()},
    }}))
    .unwrap();

    let vanilla = serde_json::to_vec(&json!({
        "id": "1.8.9",
        "downloads": {"client": {"url": format!("{base}/client.jar"), "sha1": sha1(&client_jar)}},
        "libraries": [
            {"name": "com.example:lib:1.0", "downloads": {"artifact": {
                "path": "com/example/lib/1.0/lib-1.0.jar", "url": format!("{base}/mojang/com/example/lib/1.0/lib-1.0.jar"), "sha1": sha1(&lib)}}},
            {"name": "com.example:macos-only:1.0", "rules": [{"action": "allow", "os": {"name": "osx"}}],
             "downloads": {"artifact": {"path": "x/y.jar", "url": format!("{base}/never"), "sha1": "00"}}},
            {"name": "org.lwjgl:platform:1.0", "natives": {"linux": "natives-linux", "windows": "natives-windows", "osx": "natives-osx"},
             "downloads": {"classifiers": {
                "natives-linux": {"path": "org/lwjgl/platform/1.0/platform-1.0-natives-linux.jar", "url": format!("{base}/natives.jar"), "sha1": sha1(&natives)},
                "natives-windows": {"path": "org/lwjgl/platform/1.0/platform-1.0-natives-windows.jar", "url": format!("{base}/natives.jar"), "sha1": sha1(&natives)},
                "natives-osx": {"path": "org/lwjgl/platform/1.0/platform-1.0-natives-osx.jar", "url": format!("{base}/natives.jar"), "sha1": sha1(&natives)}}}}
        ],
        "assetIndex": {"id": "1.8", "url": format!("{base}/index.json"), "sha1": sha1(&index)},
        "logging": {"client": {"argument": "-Dlog4j.configurationFile=${path}",
            "file": {"id": "client-1.7.xml", "url": format!("{base}/log.xml"), "sha1": sha1(&log_config)}}}
    }))
    .unwrap();
    let manifest = serde_json::to_vec(&json!({"versions": [
        {"id": "1.8.8", "url": format!("{base}/wrong.json"), "sha1": "00"},
        {"id": "1.8.9", "url": format!("{base}/1.8.9.json"), "sha1": sha1(&vanilla)}
    ]}))
    .unwrap();

    let forge_coordinate = format!("net.minecraftforge:forge:{}", larp_install::FORGE_VERSION);
    let profile = serde_json::to_vec(&json!({
        "install": {"path": forge_coordinate, "filePath": "forge-universal.jar", "target": "1.8.9-forge"},
        "versionInfo": {
            "mainClass": "net.minecraft.launchwrapper.Launch",
            "minecraftArguments": "--username ${auth_player_name}",
            "libraries": [
                {"name": forge_coordinate},
                // Primary URL 404s; found on the "Maven Central" mirror.
                {"name": "com.example:forgelib:2.0", "url": format!("{base}/missing/"), "checksums": [sha1(&forge_lib), "ffff"]},
                {"name": "com.example:serveronly:1.0", "clientreq": false}
            ]
        }
    }))
    .unwrap();
    let installer = zip(&[
        ("install_profile.json", &profile),
        ("forge-universal.jar", b"universal jar"),
    ]);
    let universal_path = format!(
        "/forge/net/minecraftforge/forge/{v}/forge-{v}-universal.jar",
        v = larp_install::FORGE_VERSION
    );
    serve(&server, &universal_path, 200, b"universal jar".to_vec()).await;

    serve(&server, "/manifest.json", 200, manifest).await;
    serve(&server, "/1.8.9.json", 200, vanilla).await;
    serve(&server, "/client.jar", 200, client_jar).await;
    serve(&server, "/mojang/com/example/lib/1.0/lib-1.0.jar", 200, lib).await;
    serve(&server, "/natives.jar", 200, natives).await;
    let installer_path = format!(
        "/forge/net/minecraftforge/forge/{v}/forge-{v}-installer.jar",
        v = larp_install::FORGE_VERSION
    );
    let installer_sha1 = sha1(&installer);
    serve(&server, &installer_path, 200, installer).await;
    serve(
        &server,
        "/central/com/example/forgelib/2.0/forgelib-2.0.jar",
        200,
        forge_lib,
    )
    .await;
    serve(
        &server,
        &format!("/res/{}/{}", &sha1(&asset)[..2], sha1(&asset)),
        200,
        asset,
    )
    .await;
    serve(&server, "/index.json", 200, index).await;
    serve(&server, "/log.xml", 200, log_config).await;

    let endpoints = Endpoints {
        manifest: format!("{base}/manifest.json"),
        mojang_libraries: format!("{base}/mojang/"),
        forge_maven: format!("{base}/forge/"),
        maven_central: format!("{base}/central/"),
        resources: format!("{base}/res/"),
        forge_profile: String::from_utf8(profile).unwrap(),
        forge_universal_sha1: sha1(b"universal jar"),
        forge_installer_sha1: installer_sha1,
    };
    Fixture { server, endpoints }
}

fn installer(fixture: &Fixture, files: &GameFiles) -> Installer {
    Installer::new(Net::new().unwrap().with_retry_base(Duration::ZERO), files.clone())
        .with_endpoints(fixture.endpoints.clone())
}

async fn request_count(server: &MockServer) -> usize {
    server.received_requests().await.unwrap_or_default().len()
}

#[tokio::test]
async fn installs_everything_then_verifies_without_downloading() {
    let f = fixture().await;
    let dir = tempfile::tempdir().unwrap();
    let files = GameFiles::new(dir.path().join("larpclient"));
    let quiet = |_: &str, _: Option<f64>| {};

    let game = installer(&f, &files).install(&quiet).await.unwrap();

    assert_eq!(game.version_id, "1.8.9-forge");
    assert_eq!(game.main_class, "net.minecraft.launchwrapper.Launch");
    assert_eq!(game.asset_index, "1.8");
    let names: Vec<String> = game
        .classpath
        .iter()
        .map(|p| p.file_name().unwrap().to_string_lossy().into_owned())
        .collect();
    assert_eq!(
        names,
        [
            format!("forge-{}.jar", larp_install::FORGE_VERSION),
            "forgelib-2.0.jar".into(),
            "lib-1.0.jar".into(),
            "1.8.9.jar".into()
        ]
    );
    for p in &game.classpath {
        assert!(p.is_file(), "{} missing", p.display());
    }
    // Natives: extracted, META-INF skipped, zip-slip entry not written outside.
    assert_eq!(
        std::fs::read(game.natives_dir.join("liblwjgl.so")).unwrap(),
        b"native code"
    );
    assert!(!game.natives_dir.join("META-INF").exists());
    assert!(!game.natives_dir.parent().unwrap().join("evil.so").exists());
    assert_eq!(
        game.logging_argument.as_deref(),
        Some("-Dlog4j.configurationFile=${path}")
    );
    assert!(game.logging_config.unwrap().is_file());
    assert_eq!(
        std::fs::read_dir(files.assets.join("objects")).unwrap().count(),
        1,
        "two names, one object"
    );

    // Second run: everything is present and verified, nothing is downloaded.
    let before = request_count(&f.server).await;
    let again = installer(&f, &files).install(&quiet).await.unwrap();
    assert_eq!(again.classpath, game.classpath);
    assert_eq!(
        request_count(&f.server).await,
        before,
        "a verified install needs no downloads"
    );
}

#[tokio::test]
async fn tampered_library_is_replaced_on_next_run() {
    let f = fixture().await;
    let dir = tempfile::tempdir().unwrap();
    let files = GameFiles::new(dir.path());
    let quiet = |_: &str, _: Option<f64>| {};
    let game = installer(&f, &files).install(&quiet).await.unwrap();
    let lib = game.classpath.iter().find(|p| p.ends_with("lib-1.0.jar")).unwrap();
    std::fs::write(lib, "tampered").unwrap();
    installer(&f, &files).install(&quiet).await.unwrap();
    assert_eq!(std::fs::read(lib).unwrap(), b"vanilla library");
}

#[tokio::test]
async fn truncated_asset_is_downloaded_again() {
    let f = fixture().await;
    let dir = tempfile::tempdir().unwrap();
    let files = GameFiles::new(dir.path());
    let quiet = |_: &str, _: Option<f64>| {};
    installer(&f, &files).install(&quiet).await.unwrap();
    let objects = files.assets.join("objects");
    let sub = std::fs::read_dir(&objects).unwrap().next().unwrap().unwrap().path();
    let asset = std::fs::read_dir(sub).unwrap().next().unwrap().unwrap().path();
    std::fs::write(&asset, "trunc").unwrap();
    installer(&f, &files).install(&quiet).await.unwrap();
    assert_eq!(std::fs::read(&asset).unwrap(), b"asset bytes");
}

fn universal_jar(files: &GameFiles) -> PathBuf {
    let v = larp_install::FORGE_VERSION;
    files
        .libraries
        .join(format!("net/minecraftforge/forge/{v}/forge-{v}.jar"))
}

async fn install_with(endpoints: Endpoints, files: &GameFiles) -> Result<larp_install::InstalledGame, InstallError> {
    Installer::new(Net::new().unwrap().with_retry_base(Duration::ZERO), files.clone())
        .with_endpoints(endpoints)
        .install(&|_: &str, _: Option<f64>| {})
        .await
}

/// Normal route: the universal jar alone (the profile is built in), the installer is never fetched.
#[tokio::test]
async fn forge_comes_from_the_universal_jar_without_the_installer() {
    let f = fixture().await;
    let dir = tempfile::tempdir().unwrap();
    let files = GameFiles::new(dir.path());
    let mut endpoints = f.endpoints.clone();
    endpoints.forge_installer_sha1 = "0".repeat(40); // would fail if it were used
    install_with(endpoints, &files).await.unwrap();
    assert_eq!(std::fs::read(universal_jar(&files)).unwrap(), b"universal jar");
    let requests = f.server.received_requests().await.unwrap();
    assert!(!requests.iter().any(|r| r.url.path().ends_with("-installer.jar")));
}

/// Forge's server has served one file but not the other: without the universal jar, use the installer.
#[tokio::test]
async fn forge_falls_back_to_the_installer() {
    let f = fixture().await;
    let dir = tempfile::tempdir().unwrap();
    let files = GameFiles::new(dir.path());
    let mut endpoints = f.endpoints.clone();
    endpoints.forge_universal_sha1 = "0".repeat(40); // the direct download fails verification
    install_with(endpoints, &files).await.unwrap();
    assert_eq!(std::fs::read(universal_jar(&files)).unwrap(), b"universal jar");
}

#[tokio::test]
async fn wrong_forge_installer_is_rejected() {
    let f = fixture().await;
    let dir = tempfile::tempdir().unwrap();
    let mut endpoints = f.endpoints.clone();
    endpoints.forge_universal_sha1 = "0".repeat(40);
    endpoints.forge_installer_sha1 = "0".repeat(40);
    let result = Installer::new(
        Net::new().unwrap().with_retry_base(Duration::ZERO),
        GameFiles::new(dir.path()),
    )
    .with_endpoints(endpoints)
    .install(&|_: &str, _: Option<f64>| {})
    .await;
    assert!(
        matches!(result, Err(InstallError::Net(larp_net::NetError::Checksum { .. }))),
        "{result:?}"
    );
}

#[tokio::test]
async fn progress_is_reported_and_never_goes_backwards() {
    let f = fixture().await;
    let dir = tempfile::tempdir().unwrap();
    let seen = std::sync::Mutex::new(Vec::<(String, Option<f64>)>::new());
    let record = |s: &str, p: Option<f64>| seen.lock().unwrap().push((s.to_owned(), p));
    installer(&f, &GameFiles::new(dir.path()))
        .install(&record)
        .await
        .unwrap();
    let seen = seen.into_inner().unwrap();
    assert_eq!(seen.last().unwrap(), &("Ready".to_owned(), Some(1.0)));
    for w in seen.windows(2) {
        if let ((a, Some(x)), (b, Some(y))) = (&w[0], &w[1]) {
            assert!(a != b || y >= x, "{a}: {x} -> {y}");
        }
    }
}

/// Guards the zip-slip assertion above: the fixture archive really contains a `..` entry.
#[test]
fn fixture_contains_a_zip_slip_entry() {
    let data = zip(&[("../evil.so", b"x")]);
    let archive = zip::ZipArchive::new(std::io::Cursor::new(data)).unwrap();
    assert!(archive.file_names().any(|n| n.is_ok_and(|n| n == "../evil.so")));
}
