plugins {
    alias(libs.plugins.dotenv)
}
buildscript {
    repositories {
        mavenCentral()
        google()
        maven(url = "https://plugins.gradle.org/m2/")
    }
    dependencies {
        classpath(libs.gradle.agp)
        classpath(libs.gradle.kotlin)
        classpath(libs.gradle.serialization)
    }
}

allprojects {
    repositories {
        mavenCentral()
        google()
        maven(url = "https://jitpack.io")
    }

    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
        kotlinOptions {
            jvmTarget = JavaVersion.VERSION_1_8.toString()
        }
    }
}

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory.asFile.get())
}
