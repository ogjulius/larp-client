//! Command-line arguments (same flags as the Java launcher's headless mode).

pub const USAGE: &str = "\
Usage: larp-client [--launch [--offline NAME]] [--install-only]

  --launch          install or verify the game, then start it
  --offline NAME    play with an offline profile instead of the selected account
  --install-only    install or verify the game without starting it
  --version         print the version
  --help            show this help";

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Mode {
    Gui,
    Launch,
    Install,
    Help,
    Version,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Args {
    pub mode: Mode,
    pub offline: Option<String>,
}

impl Args {
    pub fn parse(args: impl IntoIterator<Item = String>) -> Result<Self, String> {
        let mut out = Self {
            mode: Mode::Gui,
            offline: None,
        };
        let mut launch = false;
        let mut install = false;
        let mut args = args.into_iter();
        while let Some(arg) = args.next() {
            match arg.as_str() {
                "--launch" => launch = true,
                "--install-only" => install = true,
                "--offline" => out.offline = Some(args.next().ok_or("--offline needs a NAME")?),
                "--help" | "-h" => out.mode = Mode::Help,
                "--version" | "-V" => out.mode = Mode::Version,
                "--export-icon" => return Err("--export-icon is not available in this build".into()),
                other => return Err(format!("unknown argument {other:?}")),
            }
        }
        if matches!(out.mode, Mode::Help | Mode::Version) {
            return Ok(out);
        }
        // As in the Java launcher, --install-only wins over --launch, and --offline implies --launch.
        out.mode = if install {
            Mode::Install
        } else if launch || out.offline.is_some() {
            Mode::Launch
        } else {
            Mode::Gui
        };
        Ok(out)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn parse(args: &[&str]) -> Result<Args, String> {
        Args::parse(args.iter().map(|s| (*s).to_owned()))
    }

    #[test]
    fn modes() {
        assert_eq!(parse(&[]).unwrap().mode, Mode::Gui);
        assert_eq!(parse(&["--launch"]).unwrap().mode, Mode::Launch);
        assert_eq!(parse(&["--install-only"]).unwrap().mode, Mode::Install);
        assert_eq!(parse(&["--launch", "--install-only"]).unwrap().mode, Mode::Install);
        assert_eq!(parse(&["--help", "--launch"]).unwrap().mode, Mode::Help);
        let a = parse(&["--launch", "--offline", "Steve"]).unwrap();
        assert_eq!((a.mode, a.offline.as_deref()), (Mode::Launch, Some("Steve")));
    }

    #[test]
    fn errors() {
        assert!(parse(&["--offline"]).is_err());
        assert!(parse(&["--bogus"]).is_err());
        assert!(parse(&["--export-icon", "x.png"]).is_err());
    }
}
