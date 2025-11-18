import org.graalvm.buildtools.gradle.tasks.BuildNativeImageTask
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    java
    application
    kotlin("jvm") version "2.2.10"
    id("com.google.devtools.ksp") version "2.2.10-2.0.2"
    id("org.graalvm.buildtools.native")
    id("com.github.johnrengelman.shadow") version "8.1.1"
    id("org.noear.solon.native")
}

repositories {
    gradlePluginPortal()
    mavenCentral()
}

group = "com.coooolfan"
version = "0.6.8-BETA"
description = "A tool to download albums from Xiaomi Cloud."

val jimmerVersion = "0.9.112"

dependencies {
    implementation(platform("org.noear:solon-parent:3.6.0"))
    implementation("org.noear:solon-web") {
        exclude(group = "org.noear", module = "solon-serialization-snack3")
        exclude(group = "org.noear", module = "solon-sessionstate-local")
    }
    implementation("org.noear:solon-web-staticfiles")
    implementation("org.noear:solon-aot")
    implementation("org.noear:solon-logging-logback")
    implementation("org.jetbrains.kotlin:kotlin-stdlib:2.1.0")
    implementation("cn.dev33:sa-token-solon-plugin:1.44.0")
    implementation("org.noear:solon-serialization-jackson")
    implementation("com.squareup.okhttp3:okhttp:5.1.0")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.15.2")
    implementation("org.noear:solon-scheduling-simple")

    implementation("org.flywaydb:flyway-core:11.13.1")

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

extensions.configure(org.noear.solon.gradle.dsl.SolonExtension::class.java) {
    mainClass.set("com.coooolfan.xiaomialbumsyncer.App")
}

// avoid sqlite warnings
tasks.withType<JavaExec> {
    jvmArgs("--enable-native-access=ALL-UNNAMED")
}

tasks.withType<Test> {
    jvmArgs("--enable-native-access=ALL-UNNAMED")
}

tasks.withType<BuildNativeImageTask> {
    val options = this.options.get()
//    options.buildArgs.add("--pgo-instrument")
//    options.buildArgs.add("--pgo=${project.projectDir}/default.iprof")
}
