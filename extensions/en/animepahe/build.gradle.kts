
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("kotlinx-serialization")
}

buildscript {
    extra.apply {
        set("extVersionCode",3)
        set("extName","AnimePahe")
        set("pkgNameSuffix","en.animepahe")
        set("extClass",".AnimePahe")
    }
}

apply(from = "$rootDir/common.gradle")

dependencies {
    implementation(project(":lib:i18n"))
    implementation("dev.datlag.jsunpacker:jsunpacker:1.0.1"){
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib-jdk8")
    }
}

