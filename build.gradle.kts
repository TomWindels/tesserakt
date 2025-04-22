import org.jetbrains.dokka.gradle.DokkaTaskPartial

repositories {
    mavenCentral()
}

plugins {
    id("org.jetbrains.dokka") version "2.0.0"
}

subprojects {
    apply(plugin = "org.jetbrains.dokka")

    // https://github.com/Kotlin/dokka/issues/1727
    tasks.named<DokkaTaskPartial>("dokkaHtmlPartial") {
        moduleName.set(this@subprojects.path.substring(1).replace(":", "."))
    }
}

buildscript {
    allprojects {
        group = "dev.tesserakt"
    }
}
