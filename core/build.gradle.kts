plugins {
    id("com.android.library")
}

android {
    compileSdk = build.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = build.versions.minSdk.get().toInt()
    }

    namespace = "com.sf.tadami.extension.core"

    sourceSets {
        named("main") {
            manifest.srcFile("AndroidManifest.xml")
            res.setSrcDirs(listOf("res"))
        }
    }
    buildFeatures {
        resValues = false
        shaders = false
    }

    libraryVariants.all {
        generateBuildConfigProvider?.configure {
            enabled = false
        }
    }
}