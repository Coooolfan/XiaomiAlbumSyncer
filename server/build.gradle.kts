import org.graalvm.buildtools.gradle.tasks.BuildNativeImageTask
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    java
    application
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ksp)
    alias(libs.plugins.benManesVersions)
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

dependencies {
    implementation(platform(libs.solon.parent))
    implementation(libs.solon.web) {
        exclude(group = "org.noear", module = "solon-serialization-snack4")
        exclude(group = "org.noear", module = "solon-sessionstate-local")
    }
    implementation(libs.solon.aot)
    implementation(libs.solon.logging.logback)
    implementation(libs.sa.token.solon)
    implementation(libs.solon.serialization.jackson)
    implementation(libs.okhttp)
    implementation(libs.jackson.kotlin)
    implementation(libs.solon.scheduling.simple)

    implementation(libs.flyway.core)

    implementation(libs.coroutines.core)

    implementation(libs.jimmer.client)
    implementation(libs.jimmer.sql.kotlin)
    ksp(libs.jimmer.ksp)

    implementation(libs.hikari)
    runtimeOnly(libs.sqlite)

    testImplementation(libs.solon.test)
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
