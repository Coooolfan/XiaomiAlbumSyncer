import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    java
    kotlin("jvm") version "2.1.0"
}

repositories {
    mavenLocal()
    mavenCentral()
    maven { url = uri("https://mirrors.cloud.tencent.com/nexus/repository/maven-public/") }
}

group = "com.coooolfan"
version = "1.0"
description = "Demo project for Solon"

val jimmerVersion = "0.9.106"

dependencies {
    implementation(platform("org.noear:solon-parent:3.5.0"))
    implementation("org.noear:solon-web")
    implementation("org.noear:solon-logging-logback")
    implementation("org.jetbrains.kotlin:kotlin-stdlib:2.1.0")
    implementation("cn.dev33:sa-token-solon-plugin:1.44.0")

    implementation("org.flywaydb:flyway-core:11.11.2")

    implementation("org.babyfish.jimmer:jimmer-client:$jimmerVersion")
    implementation("org.babyfish.jimmer:jimmer-core:${jimmerVersion}")
    implementation("org.babyfish.jimmer:jimmer-core-kotlin:${jimmerVersion}")
    implementation("org.babyfish.jimmer:jimmer-sql:${jimmerVersion}")
    implementation("org.babyfish.jimmer:jimmer-sql-kotlin:${jimmerVersion}")

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

tasks.withType<Jar> {
    manifest {
        attributes.apply {
            set("Main-Class", "com.coooolfan.xiaomialbumsyncer.AppKt")
        }
    }

    dependsOn(configurations.runtimeClasspath)

    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(configurations.runtimeClasspath.get().map {
        if (it.isDirectory) it else zipTree(it)
    })

    from(sourceSets.main.get().output)
}

// avoid sqlite warnings
tasks.withType<JavaExec> {
    jvmArgs("--enable-native-access=ALL-UNNAMED")
}
tasks.withType<Test> {
    jvmArgs("--enable-native-access=ALL-UNNAMED")
}