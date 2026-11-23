//! Mojang library/argument "rules".

use crate::json;
use serde_json::Value;

/// Whether `rules` allow this platform. No rules: allowed. Otherwise the last matching rule wins,
/// and with no matching rule the library is disallowed. Rules with `features` never match (we never
/// request demo mode or custom resolution features). An invalid `os.version` regex doesn't match.
pub fn allowed(rules: &[Value], os_name: &str, os_version: &str) -> bool {
    if rules.is_empty() {
        return true;
    }
    let mut allowed = false;
    for rule in rules {
        let mut matches = true;
        if let Some(os) = json::obj(rule, "os") {
            if json::str(os, "name").is_some_and(|n| n != os_name) {
                matches = false;
            }
            if let Some(pattern) = json::str(os, "version")
                && !regex::Regex::new(pattern).is_ok_and(|re| re.is_match(os_version))
            {
                matches = false;
            }
        }
        if rule.get("features").is_some() {
            matches = false;
        }
        if matches {
            allowed = json::str(rule, "action") == Some("allow");
        }
    }
    allowed
}

#[cfg(test)]
mod tests {
    use super::*;
    use serde_json::json;

    #[test]
    fn follow_mojang_semantics() {
        let osx_only = json!([{"action": "allow", "os": {"name": "osx"}}]);
        assert!(!allowed(osx_only.as_array().unwrap(), "linux", "6.0"));
        assert!(allowed(osx_only.as_array().unwrap(), "osx", "10.15"));
        let all_but_osx = json!([{"action": "allow"}, {"action": "disallow", "os": {"name": "osx"}}]);
        assert!(allowed(all_but_osx.as_array().unwrap(), "linux", "6.0"));
        assert!(!allowed(all_but_osx.as_array().unwrap(), "osx", "10.15"));
        let old_osx = json!([{"action": "allow", "os": {"name": "osx", "version": "^10\\.5\\.\\d$"}}]);
        assert!(allowed(old_osx.as_array().unwrap(), "osx", "10.5.8"));
        assert!(!allowed(old_osx.as_array().unwrap(), "osx", "10.15.7"));
        assert!(allowed(&[], "linux", ""));
        let features = json!([{"action": "allow", "features": {"is_demo_user": true}}]);
        assert!(!allowed(features.as_array().unwrap(), "linux", ""));
        let bad_regex = json!([{"action": "allow", "os": {"version": "("}}]);
        assert!(!allowed(bad_regex.as_array().unwrap(), "linux", "6.0"));
    }
}
