package dev.larpclient.launcher.auth;

import com.google.gson.reflect.TypeToken;
import dev.larpclient.launcher.util.Json;
import dev.larpclient.launcher.util.Platform;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.List;

/** Persists accounts (including refresh tokens) to an owner-only readable file. */
public final class AccountStore {
    private final Path file;
    private final List<Account> accounts = new ArrayList<>();
    private int selected = -1;

    private static final class Data {
        List<Account> accounts = new ArrayList<>();
        int selected = -1;
    }

    public AccountStore(Path file) {
        this.file = file;
        try {
            if (Files.isRegularFile(file)) {
                Data data = Json.GSON.fromJson(Files.readString(file), new TypeToken<Data>() { }.getType());
                if (data != null && data.accounts != null) {
                    accounts.addAll(data.accounts);
                    selected = data.selected;
                }
            }
        } catch (Exception ignored) {
            // start empty
        }
        if (selected >= accounts.size()) {
            selected = accounts.isEmpty() ? -1 : 0;
        }
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
        Files.createDirectories(file.getParent());
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(tmp, Json.GSON.toJson(data));
        if (Platform.OS != Platform.Os.WINDOWS) {
            Files.setPosixFilePermissions(tmp, PosixFilePermissions.fromString("rw-------"));
        }
        Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }
}
