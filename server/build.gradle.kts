import org.graalvm.buildtools.gradle.tasks.BuildNativeImageTask
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.gradle.jvm.toolchain.JvmVendorSpec
import java.nio.file.Path as NioPath

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
version = (findProperty("appVersion") as String?)?.takeIf { it.isNotBlank() } ?: getGitVersion()
description = "A tool to download albums from Xiaomi Cloud."

val apiE2eTest = sourceSets.create("apiE2eTest") {
    resources.srcDir("src/apiE2eTest/resources")
    compileClasspath += sourceSets.main.get().output + configurations.testRuntimeClasspath.get()
    runtimeClasspath += output + compileClasspath
}

kotlin.sourceSets.named(apiE2eTest.name) {
    kotlin.srcDir("src/apiE2eTest/kotlin")
}

configurations[apiE2eTest.implementationConfigurationName]
    .extendsFrom(configurations.testImplementation.get())
configurations[apiE2eTest.runtimeOnlyConfigurationName]
    .extendsFrom(configurations.testRuntimeOnly.get())

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

    implementation(libs.webauthn4j.core)

    implementation(libs.hikari)
    runtimeOnly(libs.sqlite)

    testImplementation(libs.solon.test)
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.11.4")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.11.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.11.4")
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

// 避免 SQLite 警告
tasks.withType<JavaExec> {
    jvmArgs("--enable-native-access=ALL-UNNAMED")
}

tasks.withType<Test> {
    jvmArgs("--enable-native-access=ALL-UNNAMED")
    useJUnitPlatform()
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

val nativeMetadataSource = layout.projectDirectory.dir("src/main/resources/META-INF/native-image")
val nativeAgentMerged = layout.buildDirectory.dir("native-agent-merged")
val xiaomiCloudMockDir = layout.projectDirectory.dir("../xiaomi-cloud-mock")
val xiaomiCloudMockExecutable = layout.buildDirectory.file(
    "xiaomi-cloud-mock/${if (System.getProperty("os.name").startsWith("Windows")) "xiaomi-cloud-mock.exe" else "xiaomi-cloud-mock"}"
)
val xiaomiCloudMockScenario = xiaomiCloudMockDir.file("scenarios/default.json")
val xiaomiCloudMockMemoryScenario = xiaomiCloudMockDir.file("scenarios/memory-profile.json")
val xiaomiCloudMockProfilingScenario = xiaomiCloudMockDir.file("scenarios/memory-profile-small.json")
val graalVmLauncher = javaToolchains.launcherFor {
    languageVersion.set(JavaLanguageVersion.of(25))
    vendor.set(JvmVendorSpec.GRAAL_VM)
}

val buildXiaomiCloudMock by tasks.registering(Exec::class) {
    group = "verification"
    description = "构建有状态小米云模拟服务，供 API E2E 使用"
    workingDir(xiaomiCloudMockDir)
    inputs.dir(xiaomiCloudMockDir.dir("cmd"))
    inputs.dir(xiaomiCloudMockDir.dir("internal"))
    inputs.file(xiaomiCloudMockDir.file("go.mod"))
    outputs.file(xiaomiCloudMockExecutable)
    doFirst {
        xiaomiCloudMockExecutable.get().asFile.parentFile.mkdirs()
        environment("GOCACHE", layout.buildDirectory.dir("go-build-cache").get().asFile.absolutePath)
        commandLine(
            "go", "build", "-trimpath", "-o",
            xiaomiCloudMockExecutable.get().asFile.absolutePath,
            "./cmd/xiaomi-cloud-mock"
        )
    }
}

val prepareNativeMetadataMerge by tasks.registering(Sync::class) {
    group = "native"
    description = "复制当前 Native Image 元数据，供 tracing agent 增量合并"
    from(nativeMetadataSource)
    into(nativeAgentMerged)
}

fun Test.configureApiE2e(target: String, memoryBenchmark: Boolean = false) {
    group = "verification"
    testClassesDirs = apiE2eTest.output.classesDirs
    classpath = apiE2eTest.runtimeClasspath
    dependsOn(tasks.named(apiE2eTest.classesTaskName), tasks.named("classes"), buildXiaomiCloudMock)
    useJUnitPlatform {
        if (memoryBenchmark) includeTags("memory-benchmark") else excludeTags("memory-benchmark")
    }
    maxParallelForks = 1
    outputs.upToDateWhen { false }
    systemProperty("xiaomi.e2e.target", target)
    systemProperty("xiaomi.e2e.mockExecutable", xiaomiCloudMockExecutable.get().asFile.absolutePath)
    systemProperty("xiaomi.e2e.mockScenario", xiaomiCloudMockScenario.asFile.absolutePath)
}

val apiE2eJvm by tasks.registering(Test::class) {
    description = "在普通 JVM 应用进程上执行黑盒 API E2E 测试"
    configureApiE2e("jvm")
    doFirst {
        systemProperty("xiaomi.e2e.appClasspath", sourceSets.main.get().runtimeClasspath.asPath)
        systemProperty(
            "xiaomi.e2e.javaExecutable",
            NioPath.of(System.getProperty("java.home"), "bin", "java").toString()
        )
    }
}

val apiMemoryBenchmarkJvm by tasks.registering(Test::class) {
    description = "使用大尺寸有状态 Mock 场景采集 JVM 首次、空任务和增量同步内存基线"
    configureApiE2e("jvm", memoryBenchmark = true)
    doFirst {
        systemProperty("xiaomi.e2e.appClasspath", sourceSets.main.get().runtimeClasspath.asPath)
        systemProperty(
            "xiaomi.e2e.javaExecutable",
            NioPath.of(System.getProperty("java.home"), "bin", "java").toString()
        )
        val benchmarkScenario = providers.gradleProperty("xiaomi.benchmark.scenario").orNull
            ?.let(::file)
            ?: if (providers.gradleProperty("xiaomi.benchmark.jfr").orNull == "true") {
                xiaomiCloudMockProfilingScenario.asFile
            } else {
                xiaomiCloudMockMemoryScenario.asFile
            }
        systemProperty("xiaomi.e2e.mockScenario", benchmarkScenario.absolutePath)
        systemProperty(
            "xiaomi.benchmark.outputDir",
            layout.buildDirectory.dir("reports/xiaomi-memory-benchmark").get().asFile.absolutePath
        )
        listOf(
            "xiaomi.benchmark.downloaders",
            "xiaomi.benchmark.fetchFromDbSize",
            "xiaomi.benchmark.verifiers",
            "xiaomi.benchmark.exifProcessors",
            "xiaomi.benchmark.fileTimeWorkers",
            "xiaomi.benchmark.incrementalCount",
            "xiaomi.benchmark.timeoutMinutes",
            "xiaomi.benchmark.keepWorkDir",
            "xiaomi.benchmark.jfr",
            "xiaomi.benchmark.maxHeap",
            "xiaomi.benchmark.softMaxHeap",
            "xiaomi.benchmark.periodicGcInterval",
            "xiaomi.benchmark.rewriteExifTime",
            "xiaomi.benchmark.contentMode",
        ).forEach { name ->
            providers.gradleProperty(name).orNull?.let { systemProperty(name, it) }
        }
    }
}

val collectNativeMetadata by tasks.registering(Test::class) {
    description = "使用 Native Image tracing agent 执行 API E2E 并合并元数据到 build 目录"
    configureApiE2e("jvm")
    dependsOn(prepareNativeMetadataMerge)
    doFirst {
        val currentJavaHome = NioPath.of(System.getProperty("java.home"))
        val currentJavaHasAgent = currentJavaHome.resolve("lib").toFile()
            .listFiles()
            ?.any { it.name.contains("native-image-agent") }
            ?: false
        val agentJavaExecutable = if (currentJavaHasAgent) {
            currentJavaHome.resolve("bin/java").toString()
        } else {
            graalVmLauncher.get().executablePath.asFile.absolutePath
        }
        systemProperty("xiaomi.e2e.appClasspath", sourceSets.main.get().runtimeClasspath.asPath)
        systemProperty("xiaomi.e2e.javaExecutable", agentJavaExecutable)
        systemProperty("xiaomi.e2e.agentConfigDir", nativeAgentMerged.get().asFile.absolutePath)
    }
}

val apiE2eNative by tasks.registering(Test::class) {
    description = "在 Native Image 可执行文件上执行黑盒 API E2E 测试"
    configureApiE2e("native")
    dependsOn(tasks.named("nativeCompile"))
    doFirst {
        val executableName = if (System.getProperty("os.name").startsWith("Windows")) {
            "XiaomiAlbumSyncer.exe"
        } else {
            "XiaomiAlbumSyncer"
        }
        systemProperty(
            "xiaomi.e2e.appExecutable",
            layout.buildDirectory.file("native/nativeCompile/$executableName").get().asFile.absolutePath
        )
    }
}

tasks.register<Sync>("updateNativeMetadata") {
    group = "native"
    description = "执行 API E2E 收集，并将合并后的 Native Image 元数据更新到源码目录"
    dependsOn(collectNativeMetadata)
    from(nativeAgentMerged)
    into(nativeMetadataSource)
}

tasks.register("checkNativeMetadata") {
    group = "verification"
    description = "执行 API E2E 收集，并检查已提交的 Native Image 元数据是否最新"
    dependsOn(collectNativeMetadata)
    doLast {
        val sourceRoot = nativeMetadataSource.asFile
        val mergedRoot = nativeAgentMerged.get().asFile
        val sourceFiles = sourceRoot.walkTopDown()
            .filter { it.isFile }
            .associateBy { it.relativeTo(sourceRoot).invariantSeparatorsPath }
        val mergedFiles = mergedRoot.walkTopDown()
            .filter { it.isFile }
            .associateBy { it.relativeTo(mergedRoot).invariantSeparatorsPath }

        val differentFiles = (sourceFiles.keys + mergedFiles.keys).filter { relativePath ->
            val sourceFile = sourceFiles[relativePath]
            val mergedFile = mergedFiles[relativePath]
            sourceFile == null || mergedFile == null || sourceFile.readText().trimEnd() != mergedFile.readText().trimEnd()
        }
        check(differentFiles.isEmpty()) {
            "Native Image 元数据需要更新: ${differentFiles.joinToString()}。请执行 ./gradlew updateNativeMetadata"
        }
    }
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
