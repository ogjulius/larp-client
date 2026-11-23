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
    if (name != "compileGamepatchJava") {
        options.release.set(17)
    }
}

tasks.test {
    useJUnitPlatform()
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
