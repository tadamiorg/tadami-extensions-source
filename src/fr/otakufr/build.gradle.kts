
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("kotlinx-serialization")
}

buildscript {
    extra.apply {
        set("extVersionCode",4)
        set("extName","OtakuFr")
        set("pkgNameSuffix","fr.otakufr")
        set("extClass",".OtakuFr")
    }
}

apply(from = "$rootDir/common.gradle")

dependencies {
    implementation(project(":lib-voe-extractor"))
    implementation(project(":lib-sendvid-extractor"))
    implementation(project(":lib-okru-extractor"))
    implementation(project(":lib-dood-extractor"))
    implementation(project(":lib-streamwish-extractor"))
    implementation(project(":lib-sibnet-extractor"))
    implementation(project(":lib-i18n"))
    implementation(project(":lib-playlist-utils"))
    implementation("dev.datlag.jsunpacker:jsunpacker:1.0.1"){
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib-jdk8")
    }
}

