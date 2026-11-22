package dev.larpclient.launcher.game;

import java.nio.file.Path;
import java.util.List;

/** Everything needed to build a launch command, produced by {@link Installer}. */
public final class InstalledGame {
    public final String versionId;
    public final String mainClass;
    public final String minecraftArguments;
    public final String assetIndex;
    public final List<Path> classpath;
    public final Path nativesDir;
    public final Path loggingConfig;
    public final String loggingArgument;

    public InstalledGame(String versionId, String mainClass, String minecraftArguments, String assetIndex,
                         List<Path> classpath, Path nativesDir, Path loggingConfig, String loggingArgument) {
        this.versionId = versionId;
        this.mainClass = mainClass;
        this.minecraftArguments = minecraftArguments;
        this.assetIndex = assetIndex;
        this.classpath = classpath;
        this.nativesDir = nativesDir;
        this.loggingConfig = loggingConfig;
        this.loggingArgument = loggingArgument;
    }
}
