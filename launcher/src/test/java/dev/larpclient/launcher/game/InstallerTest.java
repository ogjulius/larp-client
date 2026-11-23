package dev.larpclient.launcher.game;

import com.google.gson.JsonObject;
import dev.larpclient.launcher.util.Json;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

class InstallerTest {
    private static final Path DATA = Paths.get(System.getProperty("larp.testdata", "../testdata/launcher"));

    private static List<Library> resolve(String os) throws IOException {
        JsonObject vanilla = Json.parse(Files.readString(DATA.resolve("1.8.9.json")));
        JsonObject forge = Json.obj(Json.parse(Files.readString(DATA.resolve("forge-install_profile.json"))), "versionInfo");
        return Installer.resolveLibraries(vanilla, forge, os, "10.0", "64");
    }

    /** Regression (audit A19): every downloaded library must be verified against at least one checksum. */
    @Test
    void everyDownloadedLibraryHasAChecksum() throws IOException {
        for (String os : new String[]{"linux", "windows", "osx"}) {
            for (Library l : resolve(os)) {
                if (l.url == null) {
                    continue; // the Forge universal jar comes out of the (verified) installer
                }
                assertFalse(l.checksums.isEmpty(), os + ": " + l.key + " would be downloaded unverified");
            }
        }
    }

    /** Regression (audit A19): a Forge entry must not discard the checksum vanilla has for the same file. */
    @Test
    void forgeEntriesReuseVanillaChecksums() throws IOException {
        Library jopt = resolve("linux").stream().filter(l -> l.key.equals("net.sf.jopt-simple:jopt-simple")).findFirst().orElseThrow();
        assertEquals(List.of("306816fb57cf94f108a43c95731b08934dcae15c"), jopt.checksums);
    }

    @Test
    void forgeChecksumListsAreKept() throws IOException {
        Library akka = resolve("linux").stream().filter(l -> l.key.equals("com.typesafe.akka:akka-actor_2.11")).findFirst().orElseThrow();
        assertEquals(2, akka.checksums.size());
        Library forge = resolve("linux").get(0);
        assertNull(forge.url);
    }
}
