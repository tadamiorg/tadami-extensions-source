include(":core")
include(":api")

// Load all modules under /lib
File(rootDir, "lib").eachDir { include("lib:${it.name}") }

File(rootDir, "lib-multiexts").eachDir { include("lib-multiexts:${it.name}") }

if (System.getenv("CI") != "true") {
    /**
     * Add or remove modules to load as needed for local development here.
     */
    loadAllIndividualExtensions()
} else {
    // Running in CI (GitHub Actions)

    val chunkSize = System.getenv("CI_CHUNK_SIZE").toInt()
    val chunk = System.getenv("CI_CHUNK_NUM").toInt()

    // Loads individual extensions
    File(rootDir, "extensions").getChunk(chunk, chunkSize)?.forEach {
        loadIndividualExtension(it.parentFile.name, it.name)
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
    versionCatalogs {
        create("build") {
            from(files("gradle/build.versions.toml"))
        }
    }
}
