plugins {
    id("com.android.library")
    id("kotlinx-serialization")
    kotlin("android")
}

android {
    compileSdk = build.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = build.versions.minSdk.get().toInt()
    }

    namespace = "com.sf.tadami.libs.fembedextractor"
}

repositories {
    mavenCentral()
}

dependencies {
    compileOnly(libs.kotlin.stdlib)
    compileOnly(libs.kotlin.json)
    compileOnly(libs.okhttp)
}
