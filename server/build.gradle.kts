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


dependencies {
    implementation(platform("org.noear:solon-parent:3.5.0"))
    
    implementation("org.noear:solon-web")
    implementation("org.noear:solon-view-freemarker")
    implementation("org.noear:solon-logging-logback")
    implementation("org.jetbrains.kotlin:kotlin-stdlib:2.1.0")

    compileOnly("org.projectlombok:lombok")
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