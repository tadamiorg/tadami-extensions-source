
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("kotlinx-serialization")
}

buildscript {
    extra.apply {
        set("extVersionCode",7)
        set("extName","AniSama")
        set("pkgNameSuffix","fr.anisama")
        set("extClass",".AniSama")
    }
}

apply(from = "$rootDir/common.gradle")

dependencies {
    implementation(project(":lib:voe-extractor"))
    implementation(project(":lib:filemoon-extractor"))
    implementation(project(":lib:sibnet-extractor"))
    implementation(project(":lib:sendvid-extractor"))
    implementation(project(":lib:dood-extractor"))
    implementation(project(":lib:streamhidevid-extractor"))
    implementation(project(":lib:i18n"))
    implementation(project(":lib:playlist-utils"))
}

