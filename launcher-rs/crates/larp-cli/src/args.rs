//! Command-line arguments (same flags as the Java launcher's headless mode).

pub const USAGE: &str = "\
Usage: larp-client [--launch [--offline NAME]] [--install-only] [--page NAME] [--export-icon FILE.png]

Without arguments the graphical launcher opens.

  --launch            install or verify the game, then start it
  --offline NAME      play with an offline profile instead of the selected account
  --install-only      install or verify the game without starting it
  --page NAME         open the launcher on Home, Accounts, Settings or Console
  --export-icon FILE  write the 256 px app icon as a PNG (used for desktop entries)
  --version           print the version
  --help              show this help";

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Mode {
    Gui,
    Launch,
    Install,
    Help,
    Version,
    ExportIcon,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Args {
    pub mode: Mode,
    pub offline: Option<String>,
    /// Start page of the graphical launcher.
    pub page: Option<String>,
    /// `--export-icon` target.
    pub icon: Option<String>,
}

impl Args {
    pub fn parse(args: impl IntoIterator<Item = String>) -> Result<Self, String> {
        let mut out = Self {
            mode: Mode::Gui,
            offline: None,
            page: None,
            icon: None,
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
                "--page" => out.page = Some(args.next().ok_or("--page needs a NAME")?),
                "--export-icon" => {
                    out.icon = Some(args.next().ok_or("--export-icon needs a FILE")?);
                    out.mode = Mode::ExportIcon;
                }
                other => return Err(format!("unknown argument {other:?}")),
            }
        }
        if matches!(out.mode, Mode::Help | Mode::Version | Mode::ExportIcon) {
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
        let gui = parse(&["--page", "Settings"]).unwrap();
        assert_eq!((gui.mode, gui.page.as_deref()), (Mode::Gui, Some("Settings")));
        let icon = parse(&["--export-icon", "icon.png"]).unwrap();
        assert_eq!((icon.mode, icon.icon.as_deref()), (Mode::ExportIcon, Some("icon.png")));
        let a = parse(&["--launch", "--offline", "Steve"]).unwrap();
        assert_eq!((a.mode, a.offline.as_deref()), (Mode::Launch, Some("Steve")));
    }

    #[test]
    fn errors() {
        assert!(parse(&["--offline"]).is_err());
        assert!(parse(&["--bogus"]).is_err());
        assert!(parse(&["--export-icon"]).is_err());
        assert!(parse(&["--page"]).is_err());
    }
}
