plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    kotlin("plugin.serialization")
}

android {
    compileSdkVersion = build.versions.compileSdk.get().toInteger()

    namespace = "com.sf.tadami.extensions"
    sourceSets {
        getByName("main") {
            manifest.srcFile = "AndroidManifest.xml"
            java.srcDirs = ['src']
            res.srcDirs = ['res']
            assets.srcDirs = ['assets']
        }
        getByName("release") {
            manifest.srcFile = "AndroidManifest.xml"
        }
        getByName("debug") {
            manifest.srcFile = "AndroidManifest.xml"
        }
    }

    defaultConfig {
        minSdkVersion = build.versions.minSdk.get().toInteger()
        targetSdkVersion = build.versions.targetSdk.get().toInteger()
        applicationIdSuffix = project.parent.name + "." + project.name
        versionCode = extVersionCode
        versionName = project.ext.properties.getOrDefault("libVersion", build.versions.defaultLibVersion.get()) + ".$extVersionCode"
        base {
            archivesName = "tadami-$applicationIdSuffix-v$versionName"
        }
        manifestPlaceholders = mapOf(
            "appName" to "Tadami: $extName",
            "extClass" to extClass
        )
    }

    buildTypes {
        release {
            minifyEnabled = false
        }
    }

    dependenciesInfo {
        includeInApk = false
    }

    buildFeatures {
        // Disable unused AGP features
        aidl = false
        renderScript = false
        resValues = false
        shaders = false
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_17.toString()
        freeCompilerArgs += "-opt-in=kotlinx.serialization.ExperimentalSerializationApi"
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":core"))
    api(project(":api"))
    compileOnly(libs.bundles.common)
}
