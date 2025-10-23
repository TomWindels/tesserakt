import org.jetbrains.dokka.gradle.DokkaTaskPartial

repositories {
    mavenCentral()
}

plugins {
    alias(libs.plugins.kotlin.dokka)
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
