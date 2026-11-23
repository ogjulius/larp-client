plugins {
    java
    application
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

evaluationDependsOn(":mod")

group = "dev.larpclient"
version = rootProject.property("version") as String

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(17))
}

repositories {
    mavenCentral()
}

// Classes that run inside the game JVM (Java 8), bundled as resources: see LwjglMousePatch.
val gamepatch: SourceSet = sourceSets.create("gamepatch")
tasks.named<JavaCompile>("compileGamepatchJava") {
    options.release.set(8)
}

dependencies {
    implementation("com.formdev:flatlaf:3.4.1")
    implementation("org.ow2.asm:asm:9.7")
    implementation("com.google.code.gson:gson:2.10.1")

    testImplementation(platform("org.junit:junit-bom:5.10.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

application {
    mainClass.set("dev.larpclient.launcher.Main")
    applicationDefaultJvmArgs = listOf("-Dawt.useSystemAAFontSettings=on", "-Dswing.aatext=true")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    // All warnings are errors. Suppressed:
    //  - serial:     Swing components and exceptions here are never serialized
    //  - processing: "no processor claimed these annotations" (JUnit annotations, no processors in use)
    options.compilerArgs.addAll(listOf("-Xlint:all,-serial,-processing", "-Werror"))
    if (name != "compileGamepatchJava") {
        options.release.set(17)
    }
}

tasks.test {
    useJUnitPlatform()
    // Fixtures and golden files shared with the Rust launcher (launcher-rs/).
    val testdata = rootProject.file("testdata/launcher")
    inputs.dir(testdata)
    systemProperty("larp.testdata", testdata.absolutePath)
    // ./gradlew :launcher:test -PupdateGolden  rewrites the golden files from the Java implementation.
    if (project.hasProperty("updateGolden")) {
        systemProperty("larp.updateGolden", "true")
        outputs.upToDateWhen { false }
    }
}

// Bundle the freshly built mod jar so the launcher can install it into the game instance.
val modJar = project(":mod").tasks.named("remapJar")
tasks.processResources {
    dependsOn(modJar)
    from(modJar) {
        into("larp")
        rename { "larpclient.jar.bin" } // not ".jar": Shadow would unpack it
    }
    from(gamepatch.output.classesDirs) {
        include("org/lwjgl/opengl/LarpWindowSizeCache.class")
        into("larp/gamepatch")
        eachFile { path = "larp/gamepatch/" + name + ".bin" } // keep it off the launcher's own classpath
        includeEmptyDirs = false
    }
    inputs.property("version", project.version)
    filesMatching("larp/launcher.properties") {
        expand("version" to project.version)
    }
}

tasks.shadowJar {
    archiveBaseName.set("larp-launcher")
    archiveClassifier.set("")
    manifest {
        attributes["Main-Class"] = "dev.larpclient.launcher.Main"
        attributes["Implementation-Version"] = project.version
    }
}

tasks.jar {
    archiveClassifier.set("thin")
}

tasks.assemble {
    dependsOn(tasks.shadowJar)
}

// Files the Rust launcher (launcher-rs/) embeds at build time: the mod jar, the XWayland helper class,
// and LWJGL's LinuxMouse patched by LwjglMousePatch (identical bytes to what this launcher produces).
val lwjglUrl = "https://libraries.minecraft.net/org/lwjgl/lwjgl/lwjgl/2.9.4-nightly-20150209/lwjgl-2.9.4-nightly-20150209.jar"
val lwjglSha1 = "697517568c68e78ae0b4544145af031c81082dfe"
val rustBundleDir = rootProject.layout.projectDirectory.dir("launcher-rs/bundle")

val rustBundlePatch by tasks.registering(JavaExec::class) {
    group = "larp"
    description = "Patch LWJGL's LinuxMouse for the Rust launcher bundle."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("dev.larpclient.launcher.game.RustBundle")
    val cache = layout.buildDirectory.file("rust-bundle/lwjgl-2.9.4-nightly-20150209.jar")
    args(lwjglUrl, lwjglSha1, cache.get().asFile.absolutePath, rustBundleDir.asFile.absolutePath)
    inputs.property("lwjglSha1", lwjglSha1)
    outputs.file(rustBundleDir.file("LinuxMouse.class"))
    outputs.file(rustBundleDir.file("lwjgl.sha1"))
}

tasks.register<Copy>("rustBundle") {
    group = "larp"
    description = "Write the files the Rust launcher embeds to launcher-rs/bundle/."
    dependsOn(rustBundlePatch)
    from(modJar) { rename { "larpclient.jar" } }
    from(gamepatch.output.classesDirs) {
        include("org/lwjgl/opengl/LarpWindowSizeCache.class")
        eachFile { path = name }
        includeEmptyDirs = false
    }
    into(rustBundleDir)
}

