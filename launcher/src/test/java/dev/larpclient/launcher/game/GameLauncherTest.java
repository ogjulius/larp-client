package dev.larpclient.launcher.game;

import dev.larpclient.launcher.auth.Account;
import dev.larpclient.launcher.config.LauncherSettings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameLauncherTest {
    @Test
    void splitsPlainArguments() {
        assertEquals(List.of("-Xss1M", "-Dfoo=bar"), JvmArgs.split("  -Xss1M \t -Dfoo=bar  "));
        assertEquals(List.of(), JvmArgs.split("   "));
        assertEquals(List.of(), JvmArgs.split(null));
    }

    /** Regression (audit A4): quoted values with spaces must stay one argument. */
    @Test
    void keepsQuotedArgumentsTogether() {
        assertEquals(List.of("-Dname=a b", "-Dpath=/home/me/My Games/x"),
                JvmArgs.split("-Dname=\"a b\" '-Dpath=/home/me/My Games/x'"));
        assertEquals(List.of("-Dq=say \"hi\"", ""), JvmArgs.split("-Dq=\"say \\\"hi\\\"\" \"\""));
        assertEquals(List.of("-Dwin=C:\\Games\\mc"), JvmArgs.split("-Dwin=C:\\Games\\mc"));
    }

    @Test
    void rejectsUnclosedQuote() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> JvmArgs.split("-Dx=\"oops"));
        assertTrue(e.getMessage().contains("quote"));
    }

    @Test
    void commandUsesParsedJvmArgsAndSubstitutesGameArguments(@TempDir Path dir) {
        GameFiles files = new GameFiles(dir);
        InstalledGame game = new InstalledGame("1.8.9-forge", "net.minecraft.launchwrapper.Launch",
                "--username ${auth_player_name} --accessToken ${auth_access_token} --unknown ${nope}", "1.8",
                List.of(dir.resolve("a.jar"), dir.resolve("b.jar")), dir.resolve("natives"), null, null);
        LauncherSettings settings = LauncherSettings.load(dir.resolve("launcher.json"));
        settings.jvmArgs = "-Dname=\"a b\" -Xss2M";
        Account account = Account.offline("Steve");
        account.accessToken = "secret-token";

        List<String> cmd = new GameLauncher(files).buildCommand(game, account, settings, Path.of("/java"));

        assertTrue(cmd.contains("-Dname=a b"), cmd.toString());
        assertTrue(cmd.contains("-Xss2M"));
        int user = cmd.indexOf("--username");
        assertEquals("Steve", cmd.get(user + 1));
        assertEquals("${nope}", cmd.get(cmd.indexOf("--unknown") + 1));
        assertEquals("net.minecraft.launchwrapper.Launch", cmd.get(cmd.indexOf("-cp") + 2));
        assertFalse(GameLauncher.redact(cmd, account).contains("secret-token"));
    }
}
