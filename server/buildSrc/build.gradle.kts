plugins {
    `java-gradle-plugin`
    `kotlin-dsl`
}

repositories {
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    implementation(libs.graalvm.native.plugin)
    implementation(libs.asm)
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
