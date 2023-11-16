plugins {
    kotlin("jvm") version "1.9.20"
    application
}

group = "red.cliff"
version = "1.0-SNAPSHOT"

dependencies {
    val jgitVersion = "6.7.0.202309050840-r"
    val ktorVersion = "2.3.6"

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.eclipse.jgit:org.eclipse.jgit:$jgitVersion")
    implementation("org.eclipse.jgit:org.eclipse.jgit.ssh.jsch:$jgitVersion")
    implementation("io.ktor:ktor-client-cio:$ktorVersion")
    implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-serialization-jackson:$ktorVersion")
    runtimeOnly("ch.qos.logback:logback-classic:1.4.11")
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(21)
}

application {
    mainClass.set("red.cliff.glone.MainKt")
}

tasks.getByName<JavaExec>("run") {
    val dir = project.findProperty("dir") ?: System.getProperty("user.dir")
    workingDir = project.file(dir)
}
