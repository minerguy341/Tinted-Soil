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

repositories {
    // Serves the dev-runtime test mods only. The group filters keep Gradle from consulting
    // either for anything else, so adding them cannot change how a real dependency resolves.
    maven("https://api.modrinth.com/maven") {
        name = "Modrinth"
        content { includeGroup("maven.modrinth") }
    }
    mavenCentral {
        content { includeGroup("com.electronwill.night-config") }
    }
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

    // BlockRenderLayerMap's own module before 1.21.6, folded into fabric-rendering-v1 after.
    if (sc.current.parsed < "1.21.6") fapi("fabric-blockrenderlayer-v1")

    // `fabric.mod.json` hard-depends on `fabric-api`, an id none of the individual modules
    // above provide, so the dev client needs the umbrella bundle. Runtime-only and dev-only:
    // it stays out of the published jar's metadata.
    modLocalRuntime("net.fabricmc.fabric-api:fabric-api:${sc.properties.get<String>("deps.fabric_api")}")

    // Oh The Biomes We've Gone in the dev client, to look at the blend against a worldgen
    // mod that mixes its own dirts into vanilla's. Test scaffolding, not a dependency.
    //
    // KNOWN BROKEN: reaches worldgen and dies on
    // `NoSuchMethodError: TerrablenderOverworldBiomeBuilder.method_38185`, an intermediary
    // name that survived remapping. This project compiles against Mojang mappings, so Loom
    // remaps every production mod on the way in, and a call inherited from a vanilla
    // superclass through another mod's class does not resolve. The title screen, resource
    // load and atlas work fine; creating a world does not.
    //
    // Every mod is listed because Modrinth's maven publishes bare jars with no POM, so
    // nothing resolves transitively.
    if (sc.current.version == "1.21.1") {
        modLocalRuntime("maven.modrinth:oh-the-biomes-weve-gone:2.6.0-Fabric")
        modLocalRuntime("maven.modrinth:corgilib:1.21.1-5.0.0.9-Fabric")
        // Pinned by Modrinth version id, not number: TerraBlender publishes its Fabric and
        // NeoForge builds under the same "4.1.0.8", and the maven serves the NeoForge jar.
        modLocalRuntime("maven.modrinth:terrablender:XNtIBXyQ")  // 4.1.0.8, Fabric
        // EXPERIMENT: also on the mod compile classpath, so Loom picks up its transitive
        // access widener and applies it to the Minecraft jar used for remapping.
        modCompileOnly("maven.modrinth:terrablender:XNtIBXyQ")
        modLocalRuntime("maven.modrinth:geckolib:4.9.2")
        modLocalRuntime("maven.modrinth:oh-the-trees-youll-grow:1.21.1-5.3.2-Fabric")

        // TerraBlender and CorgiLib ship NightConfig as a jar-in-jar, which Loom does not
        // unpack for a runtime-only mod. Plain libraries, not mods, so `runtimeOnly`.
        runtimeOnly("com.electronwill.night-config:core:3.8.3")
        runtimeOnly("com.electronwill.night-config:toml:3.8.3")
    }
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
