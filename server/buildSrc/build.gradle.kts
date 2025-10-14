plugins {
    `java-gradle-plugin`
    `kotlin-dsl`
}

repositories {
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    implementation("org.graalvm.buildtools:native-gradle-plugin:0.11.0")
    implementation("org.ow2.asm:asm:9.5")
    compileOnly(kotlin("gradle-plugin"))
}

gradlePlugin {
    plugins {
        register("solon") {
            id = "org.noear.solon"
            implementationClass = "org.noear.solon.gradle.plugin.SolonPlugin"
        }
        register("solonNative") {
            id = "org.noear.solon.native"
            implementationClass = "org.noear.solon.gradle.plugin.SolonNativePlugin"
        }
    }
}
