
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

buildscript {
    extra.apply {
        set("extVersionCode",34)
        set("extName","AnimeSama")
        // SAnime.language (the VF/VOSTFR badge) is lib 1.6
        set("apiVersion","1.6")
        set("pkgNameSuffix","fr.animesama")
        set("extClass",".AnimeSama")
    }
}

apply(from = "$rootDir/common.gradle")

dependencies {
    implementation(project(":lib:playlist-utils"))
    implementation(project(":lib:sendvid-extractor"))
    implementation(project(":lib:lpayer-extractor"))
    implementation(project(":lib:vk-extractor"))
    implementation(project(":lib:sibnet-extractor"))
    implementation(project(":lib:yourupload-extractor"))
    implementation(project(":lib:vidmoly-extractor"))
    implementation(project(":lib:vidhide-extractor"))
    implementation(project(":lib:uqload-extractor"))
    implementation(project(":lib:voe-extractor"))
    implementation(project(":lib:i18n"))
}

