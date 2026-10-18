include(":core")

// Load all modules under /lib
File(rootDir, "lib").eachDir { include("lib:${it.name}") }

File(rootDir, "lib-multiexts").eachDir { include("lib-multiexts:${it.name}") }

// The example extension of the template, which the template setup copies to extensions/en/example in a
// repo created from it. Built here so it keeps compiling (the release workflow builds it too).
if (File(rootDir, "template/example").isDirectory) {
    include(":template:example")
}

if (System.getenv("CI") != "true") {
    /**
     * Add or remove modules to load as needed for local development here.
     */
    loadAllIndividualExtensions()
} else {
    // Running in CI (GitHub Actions)
    val requested = System.getenv("CI_EXTENSIONS")
    val chunkSize = System.getenv("CI_CHUNK_SIZE")?.toIntOrNull()
    val chunk = System.getenv("CI_CHUNK_NUM")?.toIntOrNull()

    when {
        // Only the extensions to release, "<lang>.<name>" separated by spaces or commas
        !requested.isNullOrBlank() -> requested.split(',', ' ', '\n').filter { it.isNotBlank() }.forEach {
            val (lang, name) = it.trim().split('.', limit = 2).let { parts ->
                require(parts.size == 2) { "Extension \"$it\" is not <lang>.<name>" }
                parts[0] to parts[1]
            }
            require(File(rootDir, "extensions/$lang/$name").isDirectory) { "No extension at extensions/$lang/$name" }
            loadIndividualExtension(lang, name)
        }
        chunkSize != null && chunk != null -> File(rootDir, "extensions").getChunk(chunk, chunkSize)?.forEach {
            loadIndividualExtension(it.parentFile.name, it.name)
        }
        else -> loadAllIndividualExtensions()
    }
}

fun loadAllIndividualExtensions() {
    File(rootDir, "extensions").eachDir { dir ->
        dir.eachDir { subdir ->
            loadIndividualExtension(dir.name, subdir.name)
        }
    }
}
fun loadIndividualExtension(lang: String, name: String) {
    include("extensions:$lang:$name")
}

fun File.getChunk(chunk: Int, chunkSize: Int): List<File>? {
    return listFiles()
        // Lang folder
        ?.filter { it.isDirectory }
        // Extension subfolders
        ?.mapNotNull { dir -> dir.listFiles()?.filter { it.isDirectory } }
        ?.flatten()
        ?.sortedBy { it.name }
        ?.chunked(chunkSize)
        ?.get(chunk)
}

fun File.eachDir(block: (File) -> Unit) {
    val files = listFiles() ?: return
    for (file in files) {
        if (file.isDirectory && file.name != ".gradle" && file.name != "build") {
            block(file)
        }
    }
}

dependencyResolutionManagement {
    // Every module's repositories. JitPack serves extensions-lib (com.github.tadamiorg:tadami) and injekt
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        // -Ptadami.localLib=<version>: extensions-lib from mavenLocal, e.g. an unreleased one published by
        // ./gradlew :extensions-lib:publishToMavenLocal in the app repo (build.gradle.kts uses that version)
        if (providers.gradleProperty("tadami.localLib").isPresent) {
            mavenLocal {
                content { includeModule("com.github.tadamiorg", "tadami") }
            }
        }
        mavenCentral()
        google()
        maven(url = "https://jitpack.io")
    }
    versionCatalogs {
        create("build") {
            from(files("gradle/build.versions.toml"))
        }
    }
}
