
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("kotlinx-serialization")
}

buildscript {
    extra.apply {
        set("extVersionCode",1)
        set("extName","FrAnime")
        set("pkgNameSuffix","fr.franime")
        set("extClass",".FrAnime")
    }
}

apply(from = "$rootDir/common.gradle")

dependencies {
    implementation(project(":lib:filemoon-extractor"))
    implementation(project(":lib:sibnet-extractor"))
    implementation(project(":lib:sendvid-extractor"))
    implementation(project(":lib-multiexts:dooplay"))
    implementation(project(":lib:i18n"))
    implementation("dev.datlag.jsunpacker:jsunpacker:1.0.1"){
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib-jdk8")
    }
}

