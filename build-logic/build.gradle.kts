plugins {
    `kotlin-dsl`
}

dependencies {
    // The Kotlin plugin the modules are built with: the version the root build applies to the
    // app, so that only one of it is ever loaded.
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:2.1.0")
}
