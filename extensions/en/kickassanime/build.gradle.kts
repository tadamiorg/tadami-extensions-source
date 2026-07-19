plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("kotlinx-serialization")
}

buildscript {
    extra.apply {
        set("extVersionCode", 1)
        set("extName", "KickAssAnime")
        set("pkgNameSuffix", "en.kickassanime")
        set("extClass", ".KickAssAnime")
    }
}

apply(from = "$rootDir/common.gradle")

dependencies {
    implementation(project(":lib:i18n"))
    implementation(project(":lib:playlist-utils"))
    implementation(project(":lib:cryptoaes"))
}
