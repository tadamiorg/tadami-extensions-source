
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("kotlinx-serialization")
}

buildscript {
    extra.apply {
        set("extVersionCode",2)
        set("extName","FrAnime")
        // SAnime.language (the VF/VOSTFR badge) is lib 1.6
        set("apiVersion","1.6")
        set("pkgNameSuffix","fr.franime")
        set("extClass",".FrAnime")
    }
}

apply(from = "$rootDir/common.gradle")

dependencies {
    implementation(project(":lib:filemoon-extractor"))
    implementation(project(":lib:sibnet-extractor"))
    implementation(project(":lib:sendvid-extractor"))
    implementation(project(":lib:vidmoly-extractor"))
    implementation(project(":lib:uqload-extractor"))
    implementation(project(":lib:vk-extractor"))
    implementation(project(":lib:lpayer-extractor"))
    implementation(project(":lib:smoothpre-extractor"))
    implementation(project(":lib:vidhide-extractor"))
    implementation(project(":lib:yourupload-extractor"))
    implementation(project(":lib-multiexts:dooplay"))
    implementation(project(":lib:i18n"))
    implementation("dev.datlag.jsunpacker:jsunpacker:1.0.1"){
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib-jdk8")
    }
}

