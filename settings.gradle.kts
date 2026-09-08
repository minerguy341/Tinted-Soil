pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.fabricmc.net/") { name = "FabricMC" }
        maven("https://maven.neoforged.net/releases/") { name = "NeoForged" }
        maven("https://maven.kikugie.dev/releases") { name = "KikuGie Releases" }
        maven("https://maven.kikugie.dev/snapshots") { name = "KikuGie Snapshots" }
    }
}

plugins {
    id("dev.kikugie.stonecutter") version "0.9.8"
    // Picks the right Fabric Loom variant per Minecraft version.
    id("dev.kikugie.loom-back-compat") version "0.4.2"
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

stonecutter {
    create(rootProject) {
        /**
         * Registers one version node per loader, named `<version>-<loader>`, each using
         * `build.<loader>.gradle.kts`. All nodes share the single `src/` tree; Stonecutter
         * comments (`//? if fabric {`) select loader- and version-specific code.
         */
        fun match(version: String, vararg loaders: String) {
            for (loader in loaders) version("$version-$loader", version).buildscript("build.$loader.gradle.kts")
        }

        // NeoForge has no 1.20.1 build: `net.neoforged:neoforge` starts at 20.2. The 1.20.1
        // fork is `net.neoforged:forge:1.20.1-47.x`, which needs the legacy ModDevGradle
        // plugin, `net.minecraftforge.*` packages and SRG-named mixin refmaps. Adding it
        // means a third buildscript (`build.legacyforge.gradle.kts`) plus a third branch of
        // every loader-specific class; see README.md.
        match("1.20.1", "fabric")
        match("1.21.1", "fabric", "neoforge")
        match("1.21.8", "fabric", "neoforge")

        vcsVersion = "1.21.1-fabric"
    }
}

rootProject.name = "Tinted Soil"
