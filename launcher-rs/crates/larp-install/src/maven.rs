//! Maven coordinate helpers.

/// `group:artifact:version[:classifier][@ext]` → relative repository path. `None` if malformed.
pub fn path(coordinate: &str) -> Option<String> {
    let parts: Vec<&str> = coordinate.split(':').collect();
    if parts.len() < 3 || parts.iter().take(3).any(|p| p.is_empty()) {
        return None;
    }
    let (version, extension) = parts[2].split_once('@').unwrap_or((parts[2], "jar"));
    let classifier = parts.get(3).map(|c| format!("-{c}")).unwrap_or_default();
    Some(format!(
        "{}/{artifact}/{version}/{artifact}-{version}{classifier}.{extension}",
        parts[0].replace('.', "/"),
        artifact = parts[1]
    ))
}

/// `group:artifact[:classifier]`: identifies a library across version JSONs.
pub fn key(coordinate: &str) -> String {
    let parts: Vec<&str> = coordinate.split(':').collect();
    match parts.as_slice() {
        [g, a, _, c, ..] => format!("{g}:{a}:{c}"),
        [g, a, ..] => format!("{g}:{a}"),
        _ => coordinate.to_owned(),
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn paths_match_java_launcher() {
        assert_eq!(
            path("net.minecraft:launchwrapper:1.12").as_deref(),
            Some("net/minecraft/launchwrapper/1.12/launchwrapper-1.12.jar")
        );
        assert_eq!(
            path("org.lwjgl.lwjgl:lwjgl-platform:2.9.4:natives-linux").as_deref(),
            Some("org/lwjgl/lwjgl/lwjgl-platform/2.9.4/lwjgl-platform-2.9.4-natives-linux.jar")
        );
        assert_eq!(path("a.b:c:1@zip").as_deref(), Some("a/b/c/1/c-1.zip"));
        assert_eq!(path("bad:coordinate"), None);
        assert_eq!(key("org.ow2.asm:asm-all:5.0.3"), "org.ow2.asm:asm-all");
        assert_eq!(key("g:a:1:natives-linux"), "g:a:natives-linux");
    }
}
