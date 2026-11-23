package dev.larpclient.launcher;

import dev.larpclient.launcher.auth.Account;
import dev.larpclient.launcher.auth.AccountStore;
import dev.larpclient.launcher.config.LauncherSettings;
import dev.larpclient.launcher.game.GameFiles;
import dev.larpclient.launcher.game.GameLauncher;
import dev.larpclient.launcher.game.InstalledGame;
import dev.larpclient.launcher.game.Installer;
import dev.larpclient.launcher.game.JavaRuntime;
import dev.larpclient.launcher.game.Progress;
import dev.larpclient.launcher.util.Platform;

import java.nio.file.Path;
import java.util.List;

/**
 * Headless mode: {@code larp-launcher --launch [--offline NAME] [--install-only]}.
 * Handy for scripts, SSH sessions and debugging without the GUI.
 */
final class Cli {
    private Cli() {
    }

    static boolean wants(String[] args) {
        for (String a : args) {
            if (a.equals("--launch") || a.equals("--install-only") || a.equals("--help") || a.equals("--export-icon")) {
                return true;
            }
        }
        return false;
    }

    static int run(String[] args) throws Exception {
        String offline = null;
        boolean installOnly = false;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--offline":
                    offline = i + 1 < args.length ? args[++i] : null;
                    break;
                case "--install-only":
                    installOnly = true;
                    break;
                case "--export-icon":
                    // Used by scripts/install-linux.sh to create the desktop entry icon.
                    javax.imageio.ImageIO.write(dev.larpclient.launcher.ui.Icons.appIcon(256), "png",
                            new java.io.File(args[++i]));
                    return 0;
                case "--help":
                    System.out.println("Usage: larp-launcher [--launch [--offline NAME]] [--install-only] [--export-icon FILE.png]");
                    System.out.println("Without arguments the graphical launcher opens.");
                    return 0;
                default:
                    break;
            }
        }
        LauncherSettings settings = LauncherSettings.load(Platform.configDir().resolve("launcher.json"));
        AccountStore accounts = new AccountStore(Platform.configDir().resolve("accounts.json"));
        GameFiles files = new GameFiles(Platform.dataDir());

        Progress progress = new Progress() {
            private String lastStatus = "";
            private int lastPercent = -1;

            @Override
            public synchronized void update(String status, double fraction) {
                int percent = fraction < 0 ? -1 : (int) (fraction * 100);
                if (!status.equals(lastStatus) || (percent / 10 != lastPercent / 10)) {
                    System.out.println("[larp] " + status + (percent >= 0 ? " " + percent + "%" : ""));
                    lastStatus = status;
                    lastPercent = percent;
                }
            }
        };
        InstalledGame game = new Installer(files).install(progress);
        Path java = JavaRuntime.resolve(settings.javaPath, files, progress);
        GameLauncher launcher = new GameLauncher(files);
        launcher.prepareInstance();
        System.out.println("[larp] Java: " + java);
        System.out.println("[larp] Installed into " + files.root);
        if (installOnly) {
            return 0;
        }

        Account account;
        if (offline != null) {
            if (!Account.isValidUsername(offline)) {
                System.err.println("Invalid username: " + offline);
                return 2;
            }
            account = Account.offline(offline);
        } else {
            account = accounts.selected();
            if (account == null) {
                System.err.println("No profile selected; pass --offline NAME or add one in the GUI");
                return 2;
            }
        }
        List<String> command = launcher.buildCommand(game, account, settings, java);
        System.out.println("[larp] " + GameLauncher.redact(command, account));
        Process process = launcher.start(command, System.out::println, settings.threadedGl);
        return process.waitFor();
    }
}
