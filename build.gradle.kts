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

    // Use JVM Toolchain to ensure Java and Kotlin tasks use the same JVM target (17).
    // This resolves "Inconsistent JVM Target Compatibility" errors.
    plugins.withType<org.jetbrains.kotlin.gradle.plugin.KotlinBasePluginWrapper> {
        extensions.configure<org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension> {
            jvmToolchain(17)
        }
    }

    // Also explicitly set for Android modules to ensure AGP uses the correct version
    plugins.withId("com.android.base") {
        configure<com.android.build.gradle.BaseExtension> {
            compileOptions {
                sourceCompatibility = JavaVersion.VERSION_17
                targetCompatibility = JavaVersion.VERSION_17
            }
        }
    }

    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile>().configureEach {
        compilerOptions {
            freeCompilerArgs.add("-opt-in=kotlinx.serialization.ExperimentalSerializationApi")
        }
    }
}

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory.asFile.get())
}
