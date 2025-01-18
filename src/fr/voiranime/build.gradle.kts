
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("kotlinx-serialization")
}

buildscript {
    extra.apply {
        set("extVersionCode",1)
        set("extName","VoirAnime")
        set("pkgNameSuffix","fr.voiranime")
        set("extClass",".VoirAnime")
    }
}

apply(from = "$rootDir/common.gradle")

dependencies {
    implementation(project(":lib-voe-extractor"))
    implementation(project(":lib-mytv-extractor"))
    implementation(project(":lib-vidmoly-extractor"))
    implementation(project(":lib-streamtape-extractor"))
    implementation(project(":lib-mailru-extractor"))
    implementation(project(":lib-yourupload-extractor"))
    implementation(project(":lib-filemoon-extractor"))
    implementation(project(":lib-i18n"))
}

