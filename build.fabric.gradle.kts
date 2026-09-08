plugins {
    // Selects the correct Fabric Loom variant for the current Minecraft version.
    id("dev.kikugie.loom-back-compat")
}

version = "${property("mod.version")}+${sc.current.version}-fabric"
base.archivesName = "${property("mod.id") as String}-fabric"

val requiredJava: JavaVersion = when {
    sc.current.parsed >= "1.20.5" -> JavaVersion.VERSION_21
    else -> JavaVersion.VERSION_17
}

dependencies {
    /** Pulls only the Fabric API modules actually used, instead of the whole bundle. */
    fun fapi(vararg modules: String) {
        for (it in modules) modImplementation(fabricApi.module(it, sc.properties["deps.fabric_api"]))
    }

    minecraft("com.mojang:minecraft:${sc.current.version}")
    // Mojang mappings on both loaders, so `src/` compiles unchanged for Fabric and NeoForge.
    loomx.applyMojangMappings()

    modImplementation("net.fabricmc:fabric-loader:${property("deps.fabric_loader")}")
    fapi(
        "fabric-api-base",
        "fabric-lifecycle-events-v1",
        "fabric-resource-loader-v0",
        "fabric-rendering-v1",
        "fabric-item-group-api-v1",
    )
}

loom {
    fabricModJsonPath = rootProject.file("src/main/resources/fabric.mod.json")

    runConfigs.all {
        preferGradleTask = true
        generateRunConfig = true
        runDirectory = rootProject.file("run")
    }
}

java {
    withSourcesJar()
    targetCompatibility = requiredJava
    sourceCompatibility = requiredJava

    toolchain {
        languageVersion = JavaLanguageVersion.of(requiredJava.majorVersion)
    }
}

tasks {
    processResources {
        fun MutableMap<String, String>.register(key: String, property: String) {
            val value: String = sc.properties[property]
            inputs.property(key, value)
            set(key, value)
        }

        val props = buildMap {
            register("id", "mod.id")
            register("name", "mod.name")
            register("version", "mod.version")
            register("minecraft", "mod.mc_compat")
        }

        filesMatching("fabric.mod.json") { expand(props) }
        val mixinJava = "JAVA_${requiredJava.majorVersion}"
        filesMatching("*.mixins.json") { expand("java" to mixinJava) }

        exclude("META-INF/neoforge.mods.toml")
    }

    val modId = project.property("mod.id") as String
    withType<Jar> {
        from(rootProject.file("LICENSE")) { rename { "${it}_$modId" } }
    }

    register<Copy>("buildAndCollect") {
        group = "build"
        description = "Builds the mod jar and copies it to `build/libs/<mod version>/`"
        from(loomx.modJar.flatMap { it.archiveFile }, loomx.modSourcesJar.flatMap { it.archiveFile })
        into(rootProject.layout.buildDirectory.dir("libs/${project.property("mod.version")}"))
    }
}
