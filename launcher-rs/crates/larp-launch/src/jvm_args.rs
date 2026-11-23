//! Splits the user's "JVM arguments" setting, honouring quotes (same rules as the Java launcher).

/// - whitespace separates arguments outside quotes;
/// - single quotes are literal; inside double quotes `\"` and `\\` are escapes;
/// - backslashes outside quotes are literal, so Windows paths work unquoted.
///
/// Errors on an unclosed quote, with a message for the user.
pub fn split(text: &str) -> Result<Vec<String>, String> {
    let mut args = Vec::new();
    let mut current = String::new();
    let mut in_arg = false;
    let mut quote: Option<char> = None;
    let mut chars = text.chars().peekable();
    while let Some(c) = chars.next() {
        match quote {
            Some('\'') => {
                if c == '\'' {
                    quote = None;
                } else {
                    current.push(c);
                }
            }
            Some(_) => {
                if c == '"' {
                    quote = None;
                } else if c == '\\' && matches!(chars.peek(), Some('"' | '\\')) {
                    current.extend(chars.next());
                } else {
                    current.push(c);
                }
            }
            None if c.is_whitespace() => {
                if in_arg {
                    args.push(std::mem::take(&mut current));
                    in_arg = false;
                }
            }
            None => {
                in_arg = true;
                if c == '"' || c == '\'' {
                    quote = Some(c);
                } else {
                    current.push(c);
                }
            }
        }
    }
    if let Some(q) = quote {
        return Err(format!("unclosed {} quote", if q == '"' { "double" } else { "single" }));
    }
    if in_arg {
        args.push(current);
    }
    Ok(args)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn splits_plain_arguments() {
        assert_eq!(split("  -Xss1M \t -Dfoo=bar  ").unwrap(), ["-Xss1M", "-Dfoo=bar"]);
        assert!(split("   ").unwrap().is_empty());
    }

    #[test]
    fn keeps_quoted_arguments_together() {
        assert_eq!(
            split(r#"-Dname="a b" '-Dpath=/home/me/My Games/x'"#).unwrap(),
            ["-Dname=a b", "-Dpath=/home/me/My Games/x"]
        );
        assert_eq!(split(r#"-Dq="say \"hi\"" """#).unwrap(), [r#"-Dq=say "hi""#, ""]);
        assert_eq!(split(r"-Dwin=C:\Games\mc").unwrap(), [r"-Dwin=C:\Games\mc"]);
    }

    #[test]
    fn rejects_unclosed_quote() {
        assert!(split("-Dx=\"oops").unwrap_err().contains("quote"));
        assert!(split("-Dx='oops").unwrap_err().contains("single"));
    }
}
