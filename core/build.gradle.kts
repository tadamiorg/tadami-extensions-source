plugins {
    id("com.android.library")
}

android {
    compileSdk = build.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = build.versions.minSdk.get().toInt()
    }

    namespace = "com.sf.tadami.extensions.core"

    sourceSets {
        named("main") {
            manifest.srcFile("AndroidManifest.xml")
            res.setSrcDirs(listOf("res"))
        }
    }

    libraryVariants.all {
        generateBuildConfigProvider?.configure {
            enabled = false
        }
    }
}