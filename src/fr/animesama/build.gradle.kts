
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

buildscript {
    extra.apply {
        set("extVersionCode",8)
        set("extName","AnimeSama")
        set("pkgNameSuffix","fr.animesama")
        set("extClass",".AnimeSama")
    }
}

apply(from = "$rootDir/common.gradle")

dependencies {
    implementation(project(":lib-sendvid-extractor"))
    implementation(project(":lib-vk-extractor"))
    implementation(project(":lib-sibnet-extractor"))
    implementation(project(":lib-yourupload-extractor"))
    implementation(project(":lib-vidmoly-extractor"))
    implementation(project(":lib-i18n"))
}

