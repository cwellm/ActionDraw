// How things are stored (docs/Architecture.md, lib/core): the sidecars' format — boards and their
// cards, concepts, the registries — field for field what the files on disk hold. Callers never see
// it; they work with the domain's types, and lib:data maps between the two. No Compose and no file
// access, and it depends on nothing of ours.
plugins {
    id("actiondraw.kotlin-library")
    kotlin("plugin.serialization")
}

dependencies {
    // @Serializable is part of these classes' surface: whoever stores them needs the serializers.
    api("org.jetbrains.kotlinx:kotlinx-serialization-core:1.7.3")
}
