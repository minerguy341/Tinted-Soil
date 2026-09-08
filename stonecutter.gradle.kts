plugins {
    id("dev.kikugie.stonecutter")
}

stonecutter active "1.21.1-fabric"

stonecutter parameters {
    val (version, loader) = current.project.split('-', limit = 2)

    // Makes `[fabric."1.21.1"]`-style sections in stonecutter.properties.toml apply.
    properties {
        tags(version, loader)
    }

    // Enables `//? if fabric {` / `//? if neoforge {`.
    constants {
        match(loader, "fabric", "neoforge")
    }

    swaps["mod_version"] = "\"${properties.get<String>("mod.version")}\";"
    swaps["mod_id"] = "\"${properties.get<String>("mod.id")}\";"
    swaps["minecraft"] = "\"${node.metadata.version}\";"

    dependencies["fapi"] = properties.getOrNull<String>("deps.fabric_api") ?: "0"
}

// Stonecutter 0.9 builds inactive versions from `build/generated/stonecutter`, so every
// target can be built in one invocation without switching the active version.
stonecutter tasks {
    order("buildAndCollect")
}

tasks.register("buildAll") {
    group = "project"
    description = "Builds every registered version/loader target"
    dependsOn(stonecutter.tasks.named("buildAndCollect").map { it.values })
}
