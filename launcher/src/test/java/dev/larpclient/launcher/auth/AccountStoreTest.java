package dev.larpclient.launcher.auth;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccountStoreTest {
    @Test
    void roundTripKeepsAccountsAndSelection(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("accounts.json");
        AccountStore store = new AccountStore(file);
        store.add(Account.offline("Alice"));
        store.add(Account.offline("Bob"));
        store.select(store.all().get(0));
        store.save();

        AccountStore loaded = new AccountStore(file);
        assertEquals(2, loaded.all().size());
        assertEquals("Alice", loaded.selected().username);
        assertNull(loaded.loadProblem());
    }

    /** Regression (audit A2): a corrupt accounts file must be kept aside, never silently overwritten. */
    @Test
    void corruptFileIsQuarantinedNotOverwritten(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("accounts.json");
        String corrupt = "{\"accounts\":[{\"username\":\"Alice\",\"refreshToken\":\"secret\"";
        Files.writeString(file, corrupt);

        AccountStore store = new AccountStore(file);
        assertTrue(store.all().isEmpty());
        assertNotNull(store.loadProblem());
        store.add(Account.offline("Bob"));
        store.save();

        assertEquals(corrupt, Files.readString(dir.resolve("accounts.json.broken")));
    }

    @Test
    void emptyFileIsTreatedAsCorrupt(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("accounts.json");
        Files.writeString(file, "");
        assertNotNull(new AccountStore(file).loadProblem());
        assertTrue(Files.exists(dir.resolve("accounts.json.broken")));
    }

    @Test
    void invalidEntriesAreDroppedWithoutLosingValidOnes(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("accounts.json");
        Files.writeString(file, "{\"accounts\":[null,{\"username\":\"NoUuid\"},"
                + "{\"type\":\"OFFLINE\",\"username\":\"Alice\",\"uuid\":\"abc\"}],\"selected\":5}");
        AccountStore store = new AccountStore(file);
        assertEquals(1, store.all().size());
        assertEquals("Alice", store.selected().username);
        store.add(Account.offline("Bob")); // must not NPE on the dropped entries
        assertEquals(2, store.all().size());
    }
}
