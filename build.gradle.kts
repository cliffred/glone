plugins {
    kotlin("jvm") version "1.9.22"
    application
    id("org.graalvm.buildtools.native") version "0.10.0"
}

group = "red.cliff"
version = "1.0-SNAPSHOT"

dependencies {
    val ktorVersion = "3.3.1"
    val kotestVersion = "5.8.0"

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("io.ktor:ktor-client-cio:$ktorVersion")
    implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-serialization-jackson:$ktorVersion")
    runtimeOnly("ch.qos.logback:logback-classic:1.4.14")

    testImplementation("io.mockk:mockk:1.13.9")
    testImplementation("io.kotest:kotest-runner-junit5:$kotestVersion")
    testImplementation("io.kotest:kotest-assertions-core:$kotestVersion")
    testImplementation("ch.qos.logback:logback-classic:1.4.14")
}

tasks.test {
    useJUnitPlatform()
    jvmArgs("-XX:+EnableDynamicAgentLoading")
}

kotlin {
    jvmToolchain(21)
}

tasks.named<JavaExec>("run") {
    outputs.upToDateWhen { false }
    val dir = project.findProperty("dir") ?: System.getProperty("user.dir")
    workingDir = project.file(dir)
}

application {
    mainClass = "red.cliff.glone.MainKt"
    applicationName = project.name
}

graalvmNative {
    metadataRepository {
        enabled = true
    }
    binaries {
        named("main") {
            imageName = project.name
        }
    }
    agent {
        metadataCopy {
            inputTaskNames.add("run")
            outputDirectories.add("src/main/resources/META-INF/native-image")
            mergeWithExisting = true
        }
    }
}
