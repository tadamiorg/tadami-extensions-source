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

val libs = the<org.gradle.accessors.dm.LibrariesForLibs>()

dependencies {
    compileOnly(libs.tadami.lib.v1.get6())
    compileOnly(libs.bundles.common)
    implementation(project(":lib:playlist-utils"))
}
