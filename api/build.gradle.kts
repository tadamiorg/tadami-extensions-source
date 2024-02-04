plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.sf.tadami.extensions.api"
    compileSdk = build.versions.compileSdk.get().toInt()

    defaultConfig {
        minSdk = build.versions.minSdk.get().toInt()
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
}

dependencies {
    implementation(libs.bundles.api)
}