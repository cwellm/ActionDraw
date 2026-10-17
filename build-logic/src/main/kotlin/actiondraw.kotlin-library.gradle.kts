// A plain Kotlin module of ActionDraw: Kotlin on Java 17, tested with kotlin.test on JUnit 5.
// Nothing of Compose; a module that draws with Skia applies actiondraw.skia on top of this.
plugins {
    kotlin("jvm")
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    "testImplementation"(kotlin("test"))
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}
