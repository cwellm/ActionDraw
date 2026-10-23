plugins {
    `kotlin-dsl`
}

dependencies {
    // The Kotlin plugin the modules are built with: the version the root build applies to the
    // app, so that only one of it is ever loaded.
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:2.1.0")
    // Compose's compiler, for the UI kit's modules (actiondraw.compose): Kotlin's own, same version.
    implementation("org.jetbrains.kotlin:compose-compiler-gradle-plugin:2.1.0")
}
