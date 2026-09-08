plugins {
    id("net.neoforged.moddev") version "2.0.146"
    id("neoforge-mutex")
}

version = "${property("mod.version")}+${sc.current.version}-neoforge"
base.archivesName = "${property("mod.id") as String}-neoforge"

val requiredJava: JavaVersion = when {
    sc.current.parsed >= "1.20.5" -> JavaVersion.VERSION_21
    else -> JavaVersion.VERSION_17
}

neoForge {
    version = property("deps.neo_loader") as String

    mods {
        register(property("mod.id") as String) {
            sourceSet(sourceSets.main.get())
        }
    }

    runs {
        register("client") {
            client()
            gameDirectory = rootProject.file("run")
        }
        register("server") {
            server()
            gameDirectory = rootProject.file("run")
        }
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

        filesMatching("META-INF/neoforge.mods.toml") { expand(props) }
        val mixinJava = "JAVA_${requiredJava.majorVersion}"
        filesMatching("*.mixins.json") { expand("java" to mixinJava) }

        exclude("fabric.mod.json")
    }

    named("createMinecraftArtifacts") {
        dependsOn("stonecutterGenerate")
    }

    val modId = project.property("mod.id") as String
    withType<Jar> {
        from(rootProject.file("LICENSE")) { rename { "${it}_$modId" } }
    }

    register<Copy>("buildAndCollect") {
        group = "build"
        description = "Builds the mod jar and copies it to `build/libs/<mod version>/`"
        from(jar.flatMap { it.archiveFile }, named<Jar>("sourcesJar").flatMap { it.archiveFile })
        into(rootProject.layout.buildDirectory.dir("libs/${project.property("mod.version")}"))
    }
}
