import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

val DEV: String by project

repositories {
    mavenCentral()
}

group   = "one.wabbit"
version = "1.0.0"

plugins {
    id("com.gradleup.shadow") version "8.3.0"
    application
    kotlin("jvm") version "2.0.20"

    kotlin("plugin.serialization") version "2.0.20"
}

dependencies {
    if (DEV == "true") {
        implementation(project(":lib-std-base"))
        implementation(project(":lib-lang-parsing-parsers"))
        implementation(project(":lib-lang-parsing-charset"))
        implementation(project(":lib-lang-parsing-charinput"))
        implementation(project(":lib-levenshtein"))
        implementation(project(":lib-lang-mu"))
        implementation(project(":lib-web-imgflip"))
        implementation(project(":lib-web-scraperapi"))
        implementation(project(":lib-web-brave"))
        implementation(project(":lib-web-kagi"))
    } else {
        implementation("one.wabbit:lib-std-base:1.0.0")
        implementation("one.wabbit:lib-lang-parsing-parsers:1.0.0")
        implementation("one.wabbit:lib-lang-parsing-charset:1.0.0")
        implementation("one.wabbit:lib-lang-parsing-charinput:1.0.0")
        implementation("one.wabbit:lib-levenshtein:1.0.0")
        implementation("one.wabbit:lib-lang-mu:1.0.0")
        implementation("one.wabbit:lib-web-imgflip:1.0.0")
        implementation("one.wabbit:lib-web-scraperapi:1.0.0")
        implementation("one.wabbit:lib-web-brave:1.0.0")
        implementation("one.wabbit:lib-web-kagi:1.0.0")
    }

    testImplementation(kotlin("test"))

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.7.2")

    implementation("dev.kord:kord-core:0.13.1")
    implementation("dev.kord:kord-rest:0.13.1")
    implementation("dev.kord:kord-gateway:0.13.1")
    implementation("org.jsoup:jsoup:1.15.3")
    implementation("de.l3s.boilerpipe:boilerpipe:1.1.0")
    implementation("xerces:xercesImpl:2.12.2")
    implementation("net.sourceforge.nekohtml:nekohtml:1.9.22")
    implementation("org.jline:jline:3.25.0")
    implementation("com.aallam.openai:openai-client:3.8.1")
    runtimeOnly("org.xerial:sqlite-jdbc:3.46.1.0")
    implementation("org.jetbrains.exposed:exposed-core:0.54.0")
    implementation("org.jetbrains.exposed:exposed-jdbc:0.54.0")
    implementation("org.apache.tika:tika:2.9.2")
    implementation("org.apache.tika:tika-core:2.9.2")
    implementation("org.apache.tika:tika-parsers:2.9.2")
    implementation("org.apache.tika:tika-parser-pdf-module:2.9.2")
    implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.12")
    implementation("io.ktor:ktor-client-core:2.3.12")
    implementation("io.ktor:ktor-client-cio:2.3.12")
    implementation("io.ktor:ktor-client-content-negotiation:2.3.12")
    implementation("io.ktor:ktor-client-serialization:2.3.12")
    implementation("io.ktor:ktor-client-auth:2.3.12")
    implementation("io.ktor:ktor-server-core:2.3.12")
    implementation("io.ktor:ktor-server-netty:2.3.12")
    implementation("io.ktor:ktor-server-content-negotiation:2.3.12")
    implementation("io.ktor:ktor-server-status-pages:2.3.12")
    implementation("io.ktor:ktor-server-call-logging:2.3.12")
    implementation("io.ktor:ktor-server-cors:2.3.12")
    implementation("io.ktor:ktor-server-websockets:2.3.12")
    implementation("ch.qos.logback:logback-classic:1.4.14")
}

java {
    targetCompatibility = JavaVersion.toVersion(21)
    sourceCompatibility = JavaVersion.toVersion(21)
}

tasks {
    withType<Test> {
        jvmArgs("-ea")

    }
    withType<JavaCompile> {
        options.encoding = Charsets.UTF_8.name()
    }
    withType<Javadoc> {
        options.encoding = Charsets.UTF_8.name()
    }

    withType<KotlinCompile> {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
            freeCompilerArgs.add("-Xcontext-receivers")
        }
    }

    jar {
        setProperty("zip64", true)

    }

    application {
        mainClass.set("ducktective.MainKt")
    }

    startShadowScripts {
        dependsOn(jar)
    }

    shadowJar {
        archiveFileName.set("ducktective.jar")

        setProperty("zip64", true)

        // from(rootProject.projectDir.resolve("LICENSE"))
        // listOf(
        //     "org.slf4j"
        // ).forEach { relocate(it, "cc.shaded.$it") }
    }
}