// Build-time pieces the launcher (launcher-rs/) embeds: the mod jar, the XWayland helper class that runs
// inside the game, and LWJGL's LinuxMouse patched to use it. `./gradlew :bundle:rustBundle` writes them
// to launcher-rs/bundle/ (git-ignored); larp-launch/build.rs embeds them from there.
plugins {
    java
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

// Classes that run inside the game JVM (Java 8).
val gamepatch: SourceSet = sourceSets.create("gamepatch")

dependencies {
    implementation("org.ow2.asm:asm:9.7")

    testImplementation(platform("org.junit:junit-bom:5.10.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    // All warnings are errors. Suppressed:
    //  - processing: "no processor claimed these annotations" (JUnit annotations, no processors in use)
    options.compilerArgs.addAll(listOf("-Xlint:all,-processing", "-Werror"))
    options.release.set(if (name == "compileGamepatchJava") 8 else 17)
}

tasks.test {
    useJUnitPlatform()
}

val modJar = project(":mod").tasks.named("remapJar")
val lwjglUrl = "https://libraries.minecraft.net/org/lwjgl/lwjgl/lwjgl/2.9.4-nightly-20150209/lwjgl-2.9.4-nightly-20150209.jar"
val lwjglSha1 = "697517568c68e78ae0b4544145af031c81082dfe"
val rustBundleDir = rootProject.layout.projectDirectory.dir("launcher-rs/bundle")

val rustBundlePatch by tasks.registering(JavaExec::class) {
    group = "larp"
    description = "Patch LWJGL's LinuxMouse for the launcher bundle."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("dev.larpclient.bundle.RustBundle")
    val cache = layout.buildDirectory.file("rust-bundle/lwjgl-2.9.4-nightly-20150209.jar")
    args(lwjglUrl, lwjglSha1, cache.get().asFile.absolutePath, rustBundleDir.asFile.absolutePath)
    inputs.property("lwjglSha1", lwjglSha1)
    outputs.file(rustBundleDir.file("LinuxMouse.class"))
    outputs.file(rustBundleDir.file("lwjgl.sha1"))
}

tasks.register<Copy>("rustBundle") {
    group = "larp"
    description = "Write the files the launcher embeds to launcher-rs/bundle/."
    dependsOn(rustBundlePatch)
    from(modJar) { rename { "larpclient.jar" } }
    from(gamepatch.output.classesDirs) {
        include("org/lwjgl/opengl/LarpWindowSizeCache.class")
        eachFile { path = name }
        includeEmptyDirs = false
    }
    into(rustBundleDir)
}
