plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("kotlinx-serialization")
}

buildscript {
    extra.apply {
        set("extVersionCode",3)
        set("extName","IAnimes")
        set("pkgNameSuffix","fr.ianimes")
        set("extClass",".IAnimes")
        // SAnime.language (the VF/VOSTFR badge) is lib 1.6
        set("apiVersion","1.6")
    }
}

apply(from = "$rootDir/common.gradle")

dependencies {
    implementation(project(":lib:vidmoly-extractor"))
    implementation(project(":lib:voe-extractor"))
    implementation(project(":lib:streamtape-extractor"))
    implementation(project(":lib:mailru-extractor"))
    implementation(project(":lib:dood-extractor"))
    implementation(project(":lib:vidhide-extractor"))
    implementation(project(":lib:playlist-utils"))
    implementation(project(":lib:unpacker"))
    implementation(project(":lib:i18n"))
}
