plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

buildscript {
    extra.apply {
        // Goes up by one at every release
        set("extVersionCode", 1)
        set("extName", "Example")
        // The package is com.sf.tadami.extension.<pkgNameSuffix>: <lang>.<folder name>, never changed
        set("pkgNameSuffix", "en.example")
        set("extClass", ".Example")
        // The extensions-lib version the extension needs: older apps don't offer it
        set("apiVersion", "1.6")
    }
}

apply(from = "$rootDir/common.gradle")

dependencies {
    // Video host extractors of lib/, e.g.
    // implementation(project(":lib:voe-extractor"))
}
