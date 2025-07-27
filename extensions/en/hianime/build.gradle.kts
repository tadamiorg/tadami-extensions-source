
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

buildscript {
    extra.apply {
        set("extVersionCode",3)
        set("extName","HiAnime")
        set("pkgNameSuffix","en.hianime")
        set("extClass",".HiAnime")
    }
}

apply(from = "$rootDir/common.gradle")

dependencies {
    implementation(project(":lib:i18n"))
    implementation(project(":lib:streamtape-extractor"))
    implementation(project(":lib:megacloud-extractor"))
    implementation(project(":lib-multiexts:zoro"))
}

