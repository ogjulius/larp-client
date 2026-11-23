use crate::libraries::{self, Library, Target};
use crate::progress::{Monotonic, Progress};
use crate::{InstallError, json, maven};
use larp_core::GameFiles;
use larp_core::files::{MC_VERSION, join_slash_path};
use larp_net::{Net, run_bounded};
use serde_json::Value;
use std::collections::HashSet;
use std::io::Read;
use std::path::{Path, PathBuf};
use std::sync::atomic::{AtomicUsize, Ordering};

pub const FORGE_VERSION: &str = "1.8.9-11.15.1.2318-1.8.9";
const LIBRARY_DOWNLOADS: usize = 8;
const ASSET_DOWNLOADS: usize = 16;

/// Where metadata and files come from. The defaults are Mojang's and Forge's servers; tests point
/// these at a local server.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Endpoints {
    pub manifest: String,
    pub mojang_libraries: String,
    pub forge_maven: String,
    pub maven_central: String,
    pub resources: String,
    /// The Forge installer is fetched by coordinate, so its hash is pinned here.
    pub forge_installer_sha1: String,
}

impl Default for Endpoints {
    fn default() -> Self {
        Self {
            manifest: "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json".into(),
            mojang_libraries: "https://libraries.minecraft.net/".into(),
            forge_maven: "https://maven.minecraftforge.net/".into(),
            maven_central: "https://repo1.maven.org/maven2/".into(),
            resources: "https://resources.download.minecraft.net/".into(),
            forge_installer_sha1: "ec0293ff0776b8831f2ed90511bab76e635dda0c".into(),
        }
    }
}

/// Everything needed to build the launch command.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct InstalledGame {
    pub version_id: String,
    pub main_class: String,
    pub minecraft_arguments: String,
    pub asset_index: String,
    pub classpath: Vec<PathBuf>,
    pub natives_dir: PathBuf,
    pub logging_config: Option<PathBuf>,
    pub logging_argument: Option<String>,
}

#[derive(Debug, Clone)]
pub struct Installer {
    net: Net,
    files: GameFiles,
    endpoints: Endpoints,
    os: &'static str,
    os_version: String,
    arch: &'static str,
}

/// The OS version used by library rules. Only old-macOS rules look at it, so on platforms
/// where it doesn't matter an empty string is fine.
fn current_os_version() -> String {
    std::fs::read_to_string("/proc/sys/kernel/osrelease")
        .map(|s| s.trim().to_owned())
        .unwrap_or_default()
}

impl Installer {
    pub fn new(net: Net, files: GameFiles) -> Self {
        Self {
            net,
            files,
            endpoints: Endpoints::default(),
            os: larp_core::Os::current().mojang_name(),
            os_version: current_os_version(),
            arch: larp_core::platform::arch_bits(),
        }
    }

    #[must_use]
    pub fn with_endpoints(mut self, endpoints: Endpoints) -> Self {
        self.endpoints = endpoints;
        self
    }

    /// Install (or verify) everything and describe the result.
    pub async fn install(&self, reporter: &dyn Progress) -> Result<InstalledGame, InstallError> {
        let progress = Monotonic::new(|s: &str, f: Option<f64>| reporter.update(s, f));
        progress.update(&format!("Fetching Minecraft {MC_VERSION} metadata"), None);
        let vanilla = self.vanilla_json().await?;

        let version_dir = self.files.version_dir(MC_VERSION);
        let client = json::obj(&vanilla, "downloads")
            .and_then(|d| json::obj(d, "client"))
            .ok_or_else(|| InstallError::Metadata("version JSON has no client download".into()))?;
        let client_jar = version_dir.join(format!("{MC_VERSION}.jar"));
        progress.update(&format!("Downloading Minecraft {MC_VERSION}"), None);
        self.net
            .download(
                json::require(client, "url", "client")?,
                &client_jar,
                &[json::require(client, "sha1", "client")?.into()],
            )
            .await?;

        progress.update(&format!("Installing Forge {FORGE_VERSION}"), None);
        let profile = self.install_forge().await?;
        let forge_info = json::obj(&profile, "versionInfo")
            .ok_or_else(|| InstallError::Metadata("Forge install profile has no versionInfo".into()))?;

        let target = Target {
            os: self.os,
            os_version: &self.os_version,
            arch: self.arch,
        };
        let libraries = libraries::resolve(&vanilla, forge_info, target, &self.endpoints)?;
        let done = AtomicUsize::new(0);
        let total = libraries.len();
        run_bounded(&libraries, LIBRARY_DOWNLOADS, |library| {
            let (done, progress) = (&done, &progress);
            async move {
                self.download_library(library).await?;
                let n = done.fetch_add(1, Ordering::Relaxed) + 1;
                progress.update("Downloading libraries", Some(n as f64 / total as f64));
                Ok::<(), InstallError>(())
            }
        })
        .await?;

        progress.update("Extracting natives", None);
        let natives_dir = self.files.natives_dir(MC_VERSION);
        let natives: Vec<PathBuf> = libraries
            .iter()
            .filter(|l| l.natives)
            .map(|l| self.files.library(&l.path))
            .collect();
        let dir = natives_dir.clone();
        spawn_blocking(move || extract_natives(&natives, &dir)).await?;

        let asset_index = self.install_assets(&vanilla, &progress).await?;

        let (logging_config, logging_argument) =
            match json::obj(&vanilla, "logging").and_then(|l| json::obj(l, "client")) {
                Some(logging) => {
                    let file = json::obj(logging, "file")
                        .ok_or_else(|| InstallError::Metadata("logging config has no file".into()))?;
                    let path = self
                        .files
                        .assets
                        .join("log_configs")
                        .join(json::require(file, "id", "logging config")?);
                    self.net
                        .download(
                            json::require(file, "url", "logging config")?,
                            &path,
                            &[json::require(file, "sha1", "logging config")?.into()],
                        )
                        .await?;
                    (Some(path), json::str(logging, "argument").map(str::to_owned))
                }
                None => (None, None),
            };

        let mut classpath: Vec<PathBuf> = libraries
            .iter()
            .filter(|l| !l.natives)
            .map(|l| self.files.library(&l.path))
            .collect();
        classpath.push(client_jar);

        progress.update("Ready", Some(1.0));
        Ok(InstalledGame {
            version_id: json::obj(&profile, "install")
                .and_then(|i| json::str(i, "target"))
                .unwrap_or(MC_VERSION)
                .to_owned(),
            main_class: json::require(forge_info, "mainClass", "Forge versionInfo")?.to_owned(),
            minecraft_arguments: json::require(forge_info, "minecraftArguments", "Forge versionInfo")?.to_owned(),
            asset_index,
            classpath,
            natives_dir,
            logging_config,
            logging_argument,
        })
    }

    async fn vanilla_json(&self) -> Result<Value, InstallError> {
        let file = self.files.version_dir(MC_VERSION).join(format!("{MC_VERSION}.json"));
        if let Ok(text) = tokio::fs::read_to_string(&file).await {
            match json::parse(&text, "version JSON") {
                Ok(v) => return Ok(v),
                // Corrupt (e.g. disk trouble): fetch it again rather than failing forever.
                Err(_) => tokio::fs::remove_file(&file)
                    .await
                    .map_err(|e| InstallError::io(&file, e))?,
            }
        }
        let manifest = json::parse(&self.net.get_text(&self.endpoints.manifest).await?, "version manifest")?;
        let entry = json::arr(&manifest, "versions")
            .iter()
            .find(|v| json::str(v, "id") == Some(MC_VERSION))
            .ok_or_else(|| {
                InstallError::Metadata(format!("Minecraft {MC_VERSION} is not in Mojang's version manifest"))
            })?;
        self.net
            .download(
                json::require(entry, "url", "manifest entry")?,
                &file,
                &[json::require(entry, "sha1", "manifest entry")?.into()],
            )
            .await?;
        let text = tokio::fs::read_to_string(&file)
            .await
            .map_err(|e| InstallError::io(&file, e))?;
        json::parse(&text, "version JSON")
    }

    /// Download the (pinned) Forge installer; extract the universal jar; return the install profile.
    async fn install_forge(&self) -> Result<Value, InstallError> {
        let installer_path = maven::path(&format!("net.minecraftforge:forge:{FORGE_VERSION}:installer"))
            .ok_or_else(|| InstallError::Metadata("bad Forge coordinate".into()))?;
        let installer = self.files.library(&installer_path);
        self.net
            .download(
                &format!("{}{installer_path}", self.endpoints.forge_maven),
                &installer,
                std::slice::from_ref(&self.endpoints.forge_installer_sha1),
            )
            .await?;
        let libraries = self.files.libraries.clone();
        spawn_blocking(move || extract_forge(&installer, &libraries)).await
    }

    async fn download_library(&self, library: &Library) -> Result<(), InstallError> {
        let target = self.files.library(&library.path);
        let Some(url) = &library.url else {
            return if tokio::fs::metadata(&target).await.is_ok() {
                Ok(())
            } else {
                Err(InstallError::Metadata(format!("missing library {}", library.key)))
            };
        };
        match self.net.download(url, &target, &library.checksums).await {
            Ok(_) => Ok(()),
            Err(primary) if primary.status().is_some() => {
                for mirror in &library.mirrors {
                    match self
                        .net
                        .download(&format!("{mirror}{}", library.path), &target, &library.checksums)
                        .await
                    {
                        Ok(_) => return Ok(()),
                        Err(e) if e.status().is_some() => {} // not on this mirror; try the next
                        Err(e) => return Err(e.into()),
                    }
                }
                Err(InstallError::AllMirrorsFailed {
                    key: library.key.clone(),
                    source: primary,
                })
            }
            Err(e) => Err(e.into()),
        }
    }

    async fn install_assets(&self, vanilla: &Value, progress: &dyn Progress) -> Result<String, InstallError> {
        let info = json::obj(vanilla, "assetIndex")
            .ok_or_else(|| InstallError::Metadata("version JSON has no assetIndex".into()))?;
        let id = json::require(info, "id", "assetIndex")?.to_owned();
        let index_file = self.files.assets.join("indexes").join(format!("{id}.json"));
        progress.update("Downloading asset index", None);
        self.net
            .download(
                json::require(info, "url", "assetIndex")?,
                &index_file,
                &[json::require(info, "sha1", "assetIndex")?.into()],
            )
            .await?;
        let text = tokio::fs::read_to_string(&index_file)
            .await
            .map_err(|e| InstallError::io(&index_file, e))?;
        let index = json::parse(&text, "asset index")?;
        let objects = json::obj(&index, "objects")
            .and_then(Value::as_object)
            .ok_or_else(|| InstallError::Metadata("asset index has no objects".into()))?;

        let mut seen = HashSet::new();
        let mut assets = Vec::new();
        for object in objects.values() {
            let hash = json::require(object, "hash", "asset")?;
            if hash.len() != 40 || !hash.bytes().all(|b| b.is_ascii_hexdigit()) {
                return Err(InstallError::Metadata(format!("bad asset hash {hash:?}")));
            }
            if seen.insert(hash) {
                assets.push((hash.to_owned(), object.get("size").and_then(Value::as_u64)));
            }
        }
        let done = AtomicUsize::new(0);
        let total = assets.len();
        run_bounded(assets, ASSET_DOWNLOADS, |(hash, size)| {
            let done = &done;
            async move {
                let sub = format!("{}/{hash}", &hash[..2]);
                let target = join_slash_path(&self.files.assets.join("objects"), &sub);
                // Present with the right size is enough: hashing thousands of files every launch is slow.
                let present = tokio::fs::metadata(&target)
                    .await
                    .is_ok_and(|m| size.is_none_or(|s| m.len() == s));
                if !present {
                    self.net
                        .download(&format!("{}{sub}", self.endpoints.resources), &target, &[hash])
                        .await?;
                }
                let n = done.fetch_add(1, Ordering::Relaxed) + 1;
                if n.is_multiple_of(20) || n == total {
                    progress.update("Downloading assets", Some(n as f64 / total as f64));
                }
                Ok::<(), InstallError>(())
            }
        })
        .await?;
        Ok(id)
    }
}

async fn spawn_blocking<T: Send + 'static>(
    job: impl FnOnce() -> Result<T, InstallError> + Send + 'static,
) -> Result<T, InstallError> {
    tokio::task::spawn_blocking(job)
        .await
        .map_err(|e| InstallError::io("<worker>", std::io::Error::other(e)))?
}

fn open_zip(path: &Path) -> Result<zip::ZipArchive<std::fs::File>, InstallError> {
    let file = std::fs::File::open(path).map_err(|e| InstallError::io(path, e))?;
    zip::ZipArchive::new(file).map_err(|source| InstallError::Zip {
        path: path.to_path_buf(),
        source,
    })
}

/// Write `reader` to `target` through a temp file and rename, so a process that has the old file
/// open or mapped (a running game's natives) keeps a consistent copy.
fn write_replacing(target: &Path, reader: &mut impl Read) -> Result<(), InstallError> {
    if let Some(parent) = target.parent() {
        std::fs::create_dir_all(parent).map_err(|e| InstallError::io(parent, e))?;
    }
    let mut name = target.file_name().map(|n| n.to_os_string()).unwrap_or_default();
    name.push(".part");
    let tmp = target.with_file_name(name);
    let result = (|| {
        let mut out = std::fs::File::create(&tmp)?;
        std::io::copy(reader, &mut out)?;
        out.sync_all()?;
        std::fs::rename(&tmp, target)
    })();
    result.map_err(|e| {
        let _ = std::fs::remove_file(&tmp);
        InstallError::io(target, e)
    })
}

fn extract_forge(installer: &Path, libraries: &Path) -> Result<Value, InstallError> {
    let mut zip = open_zip(installer)?;
    let zip_err = |source| InstallError::Zip {
        path: installer.to_path_buf(),
        source,
    };
    let mut text = String::new();
    zip.by_name("install_profile.json")
        .map_err(zip_err)?
        .read_to_string(&mut text)
        .map_err(|e| InstallError::io(installer, e))?;
    let profile = json::parse(&text, "Forge install profile")?;
    let install = json::obj(&profile, "install")
        .ok_or_else(|| InstallError::Metadata("Forge install profile has no install section".into()))?;
    let universal_path = maven::path(json::require(install, "path", "Forge install")?)
        .ok_or_else(|| InstallError::Metadata("bad Forge universal coordinate".into()))?;
    let universal = join_slash_path(libraries, &universal_path);
    let mut entry = zip
        .by_name(json::require(install, "filePath", "Forge install")?)
        .map_err(zip_err)?;
    // Re-extract if missing or a different size (e.g. truncated by a crash).
    if std::fs::metadata(&universal).map(|m| m.len()).ok() != Some(entry.size()) {
        write_replacing(&universal, &mut entry)?; // the zip reader checks the entry's CRC-32
    }
    Ok(profile)
}

fn extract_natives(natives: &[PathBuf], dir: &Path) -> Result<(), InstallError> {
    std::fs::create_dir_all(dir).map_err(|e| InstallError::io(dir, e))?;
    for jar in natives {
        let mut zip = open_zip(jar)?;
        for i in 0..zip.len() {
            let mut entry = zip.by_index(i).map_err(|source| InstallError::Zip {
                path: jar.clone(),
                source,
            })?;
            if entry.is_dir() || entry.name().is_ok_and(|n| n.starts_with("META-INF")) {
                continue;
            }
            // enclosed_name() rejects absolute paths and `..` (zip-slip).
            let Some(relative) = entry.enclosed_name() else {
                continue;
            };
            write_replacing(&dir.join(relative), &mut entry)?;
        }
    }
    Ok(())
}
