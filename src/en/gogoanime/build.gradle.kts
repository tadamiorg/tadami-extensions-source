
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

buildscript {
    extra.apply {
        set("extVersionCode",4)
        set("extName","GogoAnime")
        set("pkgNameSuffix","en.gogoanime")
        set("extClass",".GogoAnime")
    }
}

apply(from = "$rootDir/common.gradle")

dependencies {
    implementation(project(":lib-streamwish-extractor"))
    implementation(project(":lib-dood-extractor"))
    implementation(project(":lib-mp4upload-extractor"))
    implementation(project(":lib-gogostream-extractor"))
    implementation(project(":lib-i18n"))
}

