
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

buildscript {
    extra.apply {
        set("extVersionCode",1)
        set("extName","VostFree")
        set("pkgNameSuffix","fr.vostfree")
        set("extClass",".VostFree")
    }
}

apply(from = "$rootDir/common.gradle")

dependencies {
    implementation(project(":lib-voe-extractor"))
    implementation(project(":lib-uqload-extractor"))
    implementation(project(":lib-sibnet-extractor"))
    implementation(project(":lib-dood-extractor"))
    implementation(project(":lib-okru-extractor"))
    implementation(project(":lib-vudeo-extractor"))
}

