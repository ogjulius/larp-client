package dev.larpclient.launcher.auth;

import com.google.gson.reflect.TypeToken;
import dev.larpclient.launcher.util.Json;
import dev.larpclient.launcher.util.SafeFiles;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Persists accounts (including refresh tokens) to an owner-only readable file. */
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
                if (data.accounts != null) {
                    for (Account a : data.accounts) {
                        if (a != null && a.uuid != null && a.username != null && a.type != null) {
                            accounts.add(a);
                        }
                    }
                }
                selected = data.selected;
            } catch (Exception e) {
                // Never overwrite a file we couldn't read: it may hold refresh tokens the user wants back.
                loadProblem = SafeFiles.quarantineAfterLoadFailure(file, e);
                System.err.println("[larp] " + loadProblem);
            }
        }
        if (selected < 0 || selected >= accounts.size()) {
            selected = accounts.isEmpty() ? -1 : 0;
        }
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
