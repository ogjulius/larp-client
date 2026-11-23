package dev.larpclient.launcher.auth;

import com.google.gson.reflect.TypeToken;
import dev.larpclient.launcher.util.Json;
import dev.larpclient.launcher.util.SafeFiles;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Persists offline profiles to an owner-only readable file (older versions kept refresh tokens in it).
 * An entry of another type, such as a Microsoft account from an older version, is loaded as an offline
 * profile with the same name and its tokens are dropped; such a file is rewritten at once so the tokens do
 * not stay on disk. Same rules as the Rust launcher.
 */
public final class AccountStore {
    private final Path file;
    private final List<Account> accounts = new ArrayList<>();
    private int selected = -1;
    private String loadProblem;

    private static final class Data {
        List<Account> accounts = new ArrayList<>();
        int selected = -1;
    }

    public AccountStore(Path file) {
        this.file = file;
        if (Files.exists(file)) {
            try {
                Data data = Json.GSON.fromJson(Files.readString(file), new TypeToken<Data>() { }.getType());
                if (data == null) {
                    throw new IOException("file is empty");
                }
                List<Account> entries = new ArrayList<>();
                boolean migrate = false;
                if (data.accounts != null) {
                    for (Account a : data.accounts) {
                        migrate |= a != null && a.type != Account.Type.OFFLINE;
                        entries.add(fromEntry(a));
                    }
                }
                String wanted = data.selected >= 0 && data.selected < entries.size() && entries.get(data.selected) != null
                        ? entries.get(data.selected).uuid : null;
                for (Account a : entries) {
                    if (a != null && accounts.stream().noneMatch(b -> b.uuid.equals(a.uuid))) {
                        accounts.add(a);
                    }
                }
                selected = -1;
                for (int i = 0; i < accounts.size(); i++) {
                    if (accounts.get(i).uuid.equals(wanted)) {
                        selected = i;
                    }
                }
                if (selected < 0 && !accounts.isEmpty()) {
                    selected = 0;
                }
                if (migrate) {
                    try {
                        save();
                    } catch (IOException e) {
                        loadProblem = file + " holds Microsoft sign-in tokens that are no longer used, and could not be"
                                + " rewritten without them (" + e.getMessage() + ").";
                    }
                }
            } catch (Exception e) {
                // Never overwrite a file we couldn't read: the user may want it back.
                loadProblem = SafeFiles.quarantineAfterLoadFailure(file, e);
                System.err.println("[larp] " + loadProblem);
            }
        }
        if (selected < 0 || selected >= accounts.size()) {
            selected = accounts.isEmpty() ? -1 : 0;
        }
    }

    /**
     * One entry as stored if it is an offline profile; otherwise (a Microsoft account from an older version)
     * a fresh offline profile with the same name. Null for entries without a name or UUID.
     */
    private static Account fromEntry(Account a) {
        if (a == null || a.username == null || a.username.isEmpty() || a.uuid == null || a.uuid.isEmpty()) {
            return null;
        }
        if (a.type == Account.Type.OFFLINE) {
            if (a.accessToken == null) {
                a.accessToken = "0";
            }
            return a;
        }
        return Account.offline(a.username);
    }

    /** A message for the user if the accounts file existed but couldn't be read, otherwise null. */
    public String loadProblem() {
        return loadProblem;
    }

    public List<Account> all() {
        return accounts;
    }

    public Account selected() {
        return selected >= 0 && selected < accounts.size() ? accounts.get(selected) : null;
    }

    public void select(Account account) {
        selected = accounts.indexOf(account);
    }

    /** Add or replace (same uuid) and select it. */
    public void add(Account account) {
        accounts.removeIf(a -> a.uuid.equals(account.uuid));
        accounts.add(account);
        selected = accounts.size() - 1;
    }

    public void remove(Account account) {
        Account current = selected();
        accounts.remove(account);
        selected = current == null || current == account ? (accounts.isEmpty() ? -1 : 0) : accounts.indexOf(current);
    }

    public void save() throws IOException {
        Data data = new Data();
        data.accounts = accounts;
        data.selected = selected;
        SafeFiles.writeAtomically(file, Json.GSON.toJson(data), true);
    }
}
