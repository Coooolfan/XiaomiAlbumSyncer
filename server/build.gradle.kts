import org.graalvm.buildtools.gradle.tasks.BuildNativeImageTask
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    java
    application
    kotlin("jvm") version "2.3.0"
    id("com.google.devtools.ksp") version "2.3.4"
    id("org.graalvm.buildtools.native")
    id("org.noear.solon.native")
}

repositories {
    gradlePluginPortal()
    mavenCentral()
}

group = "com.coooolfan"
version = getGitVersion()
description = "A tool to download albums from Xiaomi Cloud."

val jimmerVersion = "0.9.117"

dependencies {
    implementation(platform("org.noear:solon-parent:3.6.4"))
    implementation("org.noear:solon-web") {
        exclude(group = "org.noear", module = "solon-serialization-snack3")
        exclude(group = "org.noear", module = "solon-sessionstate-local")
    }
    implementation("org.noear:solon-web-staticfiles")
    implementation("org.noear:solon-aot")
    implementation("org.noear:solon-logging-logback")
    implementation("org.jetbrains.kotlin:kotlin-stdlib:2.3.0")
    implementation("cn.dev33:sa-token-solon-plugin:1.44.0")
    implementation("org.noear:solon-serialization-jackson")
    implementation("com.squareup.okhttp3:okhttp:5.1.0")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.15.2")
    implementation("org.noear:solon-scheduling-simple")

    implementation("org.flywaydb:flyway-core:11.13.1")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")

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
    jvmToolchain(25)
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
    val buildArgs = this.options.get().buildArgs
    buildArgs.add("-march=compatibility")

    // buildArgs.add("--pgo-instrument")
    // buildArgs.add("--pgo=${project.projectDir}/default.iprof")
}

val generateVersionProperties by tasks.registering {
    val outputDir = layout.buildDirectory.dir("generated/resources")
    outputs.dir(outputDir)
    doLast {
        val propsFile = outputDir.get().asFile.resolve("version.properties")
        propsFile.parentFile.mkdirs()
        propsFile.writeText("app.version=${project.version}\n")
    }
}

sourceSets {
    main {
        resources.srcDir(layout.buildDirectory.dir("generated/resources"))
    }
}

tasks.named("processResources") {
    dependsOn(generateVersionProperties)
}

fun getGitVersion(): String = try {
    val out = providers.exec {
        commandLine("git", "describe", "--tags", "--abbrev=0")
        isIgnoreExitValue = true
    }

    if (out.result.get().exitValue == 0) {
        out.standardOutput.asText.get().trim().ifEmpty { "dev" }
    } else {
        "dev"
    }
} catch (_: Exception) {
    "dev"
}
