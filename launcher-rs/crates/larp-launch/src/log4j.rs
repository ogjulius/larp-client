//! Mojang's patched log4j config for 1.8.9 prints XML events. This turns them back into readable
//! `[12:00:00] [thread/LEVEL] (logger) message` lines; other lines pass through unchanged.

use regex::Regex;
use std::sync::LazyLock;

#[allow(clippy::expect_used)] // constant patterns, covered by tests
static EVENT: LazyLock<Regex> = LazyLock::new(|| {
    Regex::new(r#"^<log4j:Event logger="([^"]*)" timestamp="(\d+)" level="([^"]*)" thread="([^"]*)">$"#)
        .expect("valid regex")
});
#[allow(clippy::expect_used)]
static MESSAGE: LazyLock<Regex> = LazyLock::new(|| Regex::new(r"<!\[CDATA\[(.*?)(\]\]>|$)").expect("valid regex"));

/// Upper bound for one event's text, so a runaway stack trace can't grow memory without limit.
const MAX_EVENT_BYTES: usize = 256 * 1024;

#[derive(Debug, Default)]
pub struct Formatter {
    header: Option<String>,
    message: String,
    in_message: bool,
}

impl Formatter {
    pub fn new() -> Self {
        Self::default()
    }

    fn append(&mut self, text: &str) {
        if self.message.len() + text.len() <= MAX_EVENT_BYTES {
            self.message.push_str(text);
        }
    }

    /// Feed one line; returns a finished line to show, if any.
    pub fn accept(&mut self, line: &str) -> Option<String> {
        if let Some(event) = EVENT.captures(line.trim()) {
            let millis = event[2].parse::<i64>().unwrap_or(0);
            let time = chrono::DateTime::from_timestamp_millis(millis)
                .map(|t| t.with_timezone(&chrono::Local).format("%H:%M:%S").to_string())
                .unwrap_or_default();
            self.header = Some(format!("[{time}] [{}/{}] ({}) ", &event[4], &event[3], &event[1]));
            self.message.clear();
            self.in_message = false;
            return None;
        }
        let Some(header) = &self.header else {
            return Some(line.to_owned());
        };
        if self.in_message {
            let end = line.find("]]>");
            self.append("\n");
            self.append(end.map_or(line, |e| &line[..e]));
            self.in_message = end.is_none();
            return None;
        }
        if line.contains("<log4j:Message>") {
            if let Some(m) = MESSAGE.captures(line) {
                let (text, closed) = (m[1].to_owned(), !m[2].is_empty());
                self.append(&text);
                self.in_message = !closed;
            }
        } else if line.contains("</log4j:Event>") {
            let out = format!("{header}{}", self.message);
            self.header = None;
            self.message.clear();
            return Some(out);
        } else if line.contains("<log4j:Throwable>") || (!line.trim().starts_with('<') && !line.trim().is_empty()) {
            // Stack traces inside <log4j:Throwable><![CDATA[ ... ]]>
            let cleaned = line
                .replace("<log4j:Throwable><![CDATA[", "")
                .replace("]]></log4j:Throwable>", "");
            self.append("\n");
            self.append(&cleaned);
        }
        None
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn run(lines: &[&str]) -> Vec<String> {
        let mut f = Formatter::new();
        lines.iter().filter_map(|l| f.accept(l)).collect()
    }

    #[test]
    fn formats_events_and_passes_plain_lines() {
        let out = run(&[
            "plain line",
            r#"<log4j:Event logger="FML" timestamp="0" level="INFO" thread="main">"#,
            "  <log4j:Message><![CDATA[Hello world]]></log4j:Message>",
            "</log4j:Event>",
        ]);
        assert_eq!(out[0], "plain line");
        assert!(out[1].ends_with("[main/INFO] (FML) Hello world"), "{}", out[1]);
    }

    #[test]
    fn multi_line_messages_and_stack_traces() {
        let out = run(&[
            r#"<log4j:Event logger="X" timestamp="0" level="ERROR" thread="t">"#,
            "<log4j:Message><![CDATA[first",
            "second]]></log4j:Message>",
            "<log4j:Throwable><![CDATA[java.lang.RuntimeException: boom",
            "\tat a.b(C.java:1)",
            "]]></log4j:Throwable>",
            "</log4j:Event>",
        ]);
        assert_eq!(out.len(), 1);
        assert!(out[0].contains("first\nsecond"), "{}", out[0]);
        assert!(
            out[0].contains("RuntimeException: boom") && out[0].contains("at a.b(C.java:1)"),
            "{}",
            out[0]
        );
    }

    #[test]
    fn huge_events_are_capped() {
        let mut f = Formatter::new();
        f.accept(r#"<log4j:Event logger="X" timestamp="0" level="ERROR" thread="t">"#);
        let big = "x".repeat(10_000);
        for _ in 0..1000 {
            f.accept(&big);
        }
        let out = f.accept("</log4j:Event>").unwrap();
        assert!(out.len() < MAX_EVENT_BYTES + 200);
    }
}
