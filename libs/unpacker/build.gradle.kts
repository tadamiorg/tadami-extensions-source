plugins {
    id("com.android.library")
    kotlin("android")
}

android {
    compileSdk = build.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = build.versions.minSdk.get().toInt()
    }

    namespace = "com.sf.tadami.libs.unpacker"
}

repositories {
    mavenCentral()
}

dependencies {
    compileOnly(libs.kotlin.stdlib)
}
