include(":core")
include(":api")

// Load all modules under /libs
File(rootDir, "lib").eachDir {
    val libName = it.name
    include(":lib-$libName")
    project(":lib-$libName").projectDir = File("lib/$libName")
}

if (System.getenv("CI") == null || System.getenv("CI_MODULE_GEN") == "true") {
    // Local development (full project build)
    loadAllExtensions()
} else {
    // Running in CI (GitHub Actions)

    val chunkSize = System.getenv("CI_CHUNK_SIZE").toInt()
    val chunk = System.getenv("CI_CHUNK_NUM").toInt()


    // Loads extensions
    File(rootDir, "src").getChunk(chunk, chunkSize)?.forEach {
        val name = ":extensions:${it.parentFile.name}:${it.name}"
        println(name)
        include(name)
        project(name).projectDir = File("src/${it.parentFile.name}/${it.name}")
    }

}

fun loadAllExtensions() {
    File(rootDir, "src").eachDir { dir ->
        dir.eachDir { subdir ->
            val name = ":extensions:${dir.name}:${subdir.name}"
            include(name)
            project(name).projectDir = File("src/${dir.name}/${subdir.name}")
        }
    }
}

fun loadExtension(lang: String, name: String) {
    val projectName = ":extensions:$lang:$name"
    include(projectName)
    project(projectName).projectDir = File("src/${lang}/${name}")
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
    listFiles()?.filter { it.isDirectory }?.forEach { block(it) }
}
dependencyResolutionManagement {

    versionCatalogs {
        create("build") {
            from(files("gradle/build.versions.toml"))
        }
    }
}
