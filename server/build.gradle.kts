import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    java
    application
    kotlin("jvm") version "2.2.10"
    id("com.google.devtools.ksp") version "2.2.10-2.0.2"
    id("org.graalvm.buildtools.native") version "0.11.0"
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

repositories {
    mavenCentral()
}

group = "com.coooolfan"
version = "1.0"
description = "Demo project for Solon"

val jimmerVersion = "0.9.106"

dependencies {
    implementation(platform("org.noear:solon-parent:3.5.1"))
    implementation("org.noear:solon-web") {
        exclude(group = "org.noear", module = "solon-serialization-snack3")
    }
    implementation("org.noear:solon-web-staticfiles")
    implementation("org.noear:solon-logging-logback")
    implementation("org.jetbrains.kotlin:kotlin-stdlib:2.1.0")
    implementation("cn.dev33:sa-token-solon-plugin:1.44.0")
    implementation("org.noear:solon-serialization-jackson:3.5.1")
    implementation("com.squareup.okhttp3:okhttp:5.1.0")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.15.2")
    implementation("org.noear:solon-scheduling-simple")

    implementation("org.flywaydb:flyway-core:11.11.2")

    implementation("org.babyfish.jimmer:jimmer-client:$jimmerVersion")
    implementation("org.babyfish.jimmer:jimmer-core:${jimmerVersion}")
    implementation("org.babyfish.jimmer:jimmer-core-kotlin:${jimmerVersion}")
    implementation("org.babyfish.jimmer:jimmer-sql:${jimmerVersion}")
    implementation("org.babyfish.jimmer:jimmer-sql-kotlin:${jimmerVersion}")
    ksp("org.babyfish.jimmer:jimmer-ksp:${jimmerVersion}")

    implementation("com.zaxxer:HikariCP:7.0.2")
    runtimeOnly("org.xerial:sqlite-jdbc:3.50.3.0")

    testImplementation("org.noear:solon-test")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.compilerArgs.add("-parameters")
}
tasks.withType<KotlinCompile> {
    compilerOptions.javaParameters = true
}

kotlin {
    sourceSets.main {
        kotlin.srcDir("build/generated/ksp/main/kotlin")
    }
}

application {
    mainClass.set("com.coooolfan.xiaomialbumsyncer.App")
}

// avoid sqlite warnings
tasks.withType<JavaExec> {
    jvmArgs("--enable-native-access=ALL-UNNAMED")
}

tasks.withType<Test> {
    jvmArgs("--enable-native-access=ALL-UNNAMED")
}

graalvmNative {
    agent {
        defaultMode.set("standard")
    }
}
