import org.apache.commons.lang3.SystemUtils

plugins {
    idea
    java
    id("gg.essential.loom") version "0.10.0.+"
    id("dev.architectury.architectury-pack200") version "0.1.3"
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

// ---------------------------------------------------------------------------
// Project constants (see gradle.properties)
// ---------------------------------------------------------------------------
val baseGroup: String by project
val mcVersion: String by project
val modid: String by project
val modName: String by project
val transformerFile = file("src/main/resources/accesstransformer.cfg")

group = baseGroup

// Minecraft 1.8.9 / LaunchWrapper requires Java 8 bytecode and runtime.
java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(8))
}

// ---------------------------------------------------------------------------
// Minecraft / Loom
// ---------------------------------------------------------------------------
loom {
    log4jConfigs.from(file("log4j2.xml"))
    launchConfigs {
        "client" {
            property("mixin.debug.export", "false")
            arg("--tweakClass", "org.spongepowered.asm.launch.MixinTweaker")
        }
    }
    runConfigs {
        "client" {
            if (SystemUtils.IS_OS_MAC_OSX) {
                vmArgs.remove("-XstartOnFirstThread")
            }
            // Sensible defaults for a PvP client dev run.
            vmArgs.addAll(listOf("-Xms1G", "-Xmx2G", "-XX:+UseG1GC"))
        }
        remove(getByName("server"))
    }
    forge {
        pack200Provider.set(dev.architectury.pack200.java.Pack200Adapter())
        mixinConfig("mixins.$modid.json")
        if (transformerFile.exists()) {
            accessTransformer(transformerFile)
        }
    }
    mixin {
        defaultRefmapName.set("mixins.$modid.refmap.json")
    }
}

sourceSets.main {
    output.setResourcesDir(sourceSets.main.flatMap { it.java.classesDirectory })
}

// ---------------------------------------------------------------------------
// Dependencies
// ---------------------------------------------------------------------------
repositories {
    mavenCentral()
    maven("https://repo.spongepowered.org/maven/")
    // DevAuth: optional real-account login in dev runs (disabled unless configured).
    maven("https://pkgs.dev.azure.com/djtheredstoner/DevAuth/_packaging/public/maven/v1")
}

val shadowImpl: Configuration by configurations.creating {
    configurations.implementation.get().extendsFrom(this)
}

dependencies {
    minecraft("com.mojang:minecraft:$mcVersion")
    mappings("de.oceanlabs.mcp:mcp_stable:22-1.8.9")
    forge("net.minecraftforge:forge:1.8.9-11.15.1.2318-1.8.9")

    // MP3 decoding for the music player (LGPL, shaded + relocated).
    shadowImpl("javazoom:jlayer:1.0.1")

    shadowImpl("org.spongepowered:mixin:0.7.11-SNAPSHOT") {
        isTransitive = false
    }
    annotationProcessor("org.spongepowered:mixin:0.8.5-SNAPSHOT")

    runtimeOnly("me.djtheredstoner:DevAuth-forge-legacy:1.2.1")

    testImplementation(platform("org.junit:junit-bom:5.10.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// ---------------------------------------------------------------------------
// Tasks
// ---------------------------------------------------------------------------
tasks.withType(JavaCompile::class) {
    options.encoding = "UTF-8"
    // All warnings are errors. Suppressed, because they aren't about our code:
    //  - classfile:  Loom's remapped Minecraft jar carries Java 8 MethodParameters in Java 6 class files
    //  - processing: "no processor claimed these annotations" from the Mixin annotation processor run
    options.compilerArgs.addAll(listOf("-Xlint:all,-classfile,-processing", "-Werror"))
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

tasks.withType(org.gradle.jvm.tasks.Jar::class) {
    archiveBaseName.set(modid)
    manifest.attributes.run {
        this["FMLCorePluginContainsFMLMod"] = "true"
        this["ForceLoadAsMod"] = "true"
        this["TweakClass"] = "org.spongepowered.asm.launch.MixinTweaker"
        this["MixinConfigs"] = "mixins.$modid.json"
        if (transformerFile.exists()) {
            this["FMLAT"] = "${modid}_at.cfg"
        }
    }
}

tasks.processResources {
    inputs.property("version", project.version)
    inputs.property("mcversion", mcVersion)
    inputs.property("modid", modid)
    inputs.property("modname", modName)
    inputs.property("basePackage", baseGroup)

    filesMatching(listOf("mcmod.info", "mixins.$modid.json")) {
        expand(inputs.properties)
    }

    rename("accesstransformer.cfg", "META-INF/${modid}_at.cfg")
}

val remapJar by tasks.named<net.fabricmc.loom.task.RemapJarTask>("remapJar") {
    archiveClassifier.set("")
    from(tasks.shadowJar)
    input.set(tasks.shadowJar.get().archiveFile)
}

tasks.jar {
    archiveClassifier.set("without-deps")
    destinationDirectory.set(layout.buildDirectory.dir("intermediates"))
}

tasks.shadowJar {
    destinationDirectory.set(layout.buildDirectory.dir("intermediates"))
    archiveClassifier.set("non-obfuscated-with-deps")
    configurations = listOf(shadowImpl)
    relocate("javazoom.jl", "$baseGroup.deps.jlayer")
}

tasks.assemble.get().dependsOn(tasks.remapJar)

// Loom 0.10's RunGameTask exposes an unannotated `getMain()` which Gradle 8 rejects at execution
// time, so `runClient` cannot run. `runGame` launches the identical Loom run configuration through
// a plain JavaExec task instead.
tasks.register<JavaExec>("runGame") {
    group = "larp"
    description = "Launch Minecraft 1.8.9 with LARP Client in a dev environment."
    dependsOn(tasks.classes, tasks.named("downloadAssets"))
    javaLauncher.set(javaToolchains.launcherFor { languageVersion.set(JavaLanguageVersion.of(8)) })
    classpath = sourceSets.main.get().runtimeClasspath
    workingDir = file("run")
    val runConfig = provider {
        net.fabricmc.loom.configuration.ide.RunConfig.runConfig(project, loom.runConfigs.getByName("client"))
    }
    mainClass.set(runConfig.map { it.mainClass })
    // ./gradlew :mod:runGame -PlarpTour [-PlarpTourScale=2]  → screenshots of every LARP screen
    if (project.hasProperty("larpTour")) {
        systemProperty("larp.screenshotTour", "true")
        systemProperty("larp.tourScale", project.findProperty("larpTourScale") ?: "2")
        systemProperty("larp.tourWidth", project.findProperty("larpTourWidth") ?: "1280")
        systemProperty("larp.tourHeight", project.findProperty("larpTourHeight") ?: "720")
        systemProperty("larp.tourMouse", project.findProperty("larpTourMouse") ?: "")
    }
    doFirst {
        workingDir.mkdirs()
        val config = runConfig.get()
        jvmArgs(config.vmArgs)
        args(config.programArgs)
    }
}
