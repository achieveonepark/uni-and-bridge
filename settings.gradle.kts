pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        google()
        mavenCentral()
        providers.gradleProperty("unityExport").orNull?.let { export ->
            flatDir {
                val library = file("$export/unityLibrary")
                dirs(library.resolve("libs"))
                library.listFiles()?.filter { it.isDirectory }?.forEach { dirs(it.resolve("libs")) }
            }
        }
    }
}

rootProject.name = "android-unity-bridge"
include(":android")

// The sample is optional: a Unity export is needed only to build an actual app.
val unityExport = providers.gradleProperty("unityExport").orNull
if (unityExport != null) {
    val unityLibrary = file("$unityExport/unityLibrary")
    require(unityLibrary.resolve("build.gradle").isFile) {
        "unityExport must point to a Unity Android Gradle export containing unityLibrary."
    }
    include(":unityLibrary", ":sample")
    project(":unityLibrary").projectDir = unityLibrary
    project(":sample").projectDir = file("samples/android")
    val exportedProperties = java.util.Properties()
    unityLibrary.parentFile.resolve("gradle.properties").takeIf { it.isFile }
        ?.inputStream()?.use { exportedProperties.load(it) }
    gradle.beforeProject {
        if (path == ":unityLibrary" || path.startsWith(":unityLibrary:")) {
            // Unity's generated build script reads this root-export property directly.
            extensions.extraProperties.set("unityStreamingAssets",
                providers.gradleProperty("unityStreamingAssets").orNull
                    ?: exportedProperties.getProperty("unityStreamingAssets", ""))
        }
    }
    // Unity may export additional Android library plug-ins as child modules.
    unityLibrary.listFiles()?.filter { it.isDirectory && it.resolve("build.gradle").isFile }
        ?.forEach {
            include(":unityLibrary:${it.name}")
            project(":unityLibrary:${it.name}").projectDir = it
        }
}
