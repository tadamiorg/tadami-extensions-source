plugins {
    id("com.android.library")
    kotlin("android")
    id("kotlinx-serialization")
}

android {
    compileSdk = build.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = build.versions.minSdk.get().toInt()
    }

    namespace = "com.sf.tadami.lib.${name.replace("-", "")}"

}

repositories {
    mavenCentral()
}


val libs = the<org.gradle.accessors.dm.LibrariesForLibs>()

dependencies {
    compileOnly(project(":api"))
    compileOnly(libs.bundles.common)
    implementation(project(":lib-playlist-utils"))
    implementation("dev.datlag.jsunpacker:jsunpacker:1.0.1"){
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib-jdk8")
    }
}
