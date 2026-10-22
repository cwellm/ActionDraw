// The data the app is made of: boards and their cards, concepts, session plans — plain
// serializable classes and the pure rules on them. No Compose and no file access, so every other
// module may depend on this one and it depends on nothing of ours (ROADMAP, M10).
plugins {
    id("actiondraw.kotlin-library")
    kotlin("plugin.serialization")
}

dependencies {
    // @Serializable is part of these classes' surface: whoever stores them needs the serializers.
    api("org.jetbrains.kotlinx:kotlinx-serialization-core:1.7.3")
}
