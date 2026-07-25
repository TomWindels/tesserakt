repositories {
    mavenCentral()
}

plugins {
    alias(libs.plugins.kotlin.dokka)
}

subprojects {
    apply(plugin = "org.jetbrains.dokka")

    // https://github.com/Kotlin/dokka/issues/1727
    dokka {
        moduleName.set(this@subprojects.path.substring(1).replace(":", "."))
    }
}

buildscript {
    allprojects {
        group = "dev.tesserakt"
    }
}

val inCI = System.getenv("GITHUB_ACTIONS") != null

// we want to use host Node if we aren't running in CI; otherwise, we use a fixed version of node up-to-date enough
//  to deal with our NPM dependencies
// src: https://youtrack.jetbrains.com/projects/KT/issues/KT-82042/KMP-yarn-always-use-public-repo-using-WasmJS
if (inCI) {
    allprojects {
        project.plugins.withType<org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsPlugin> {
            the<org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsEnvSpec>().version.set("26.5.0")
        }
        project.plugins.withType<org.jetbrains.kotlin.gradle.targets.wasm.nodejs.WasmNodeJsPlugin> {
            the<org.jetbrains.kotlin.gradle.targets.wasm.nodejs.WasmNodeJsEnvSpec>().version.set("26.5.0")
        }
    }
} else {
    allprojects {
        project.plugins.withType<org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsPlugin> {
            the<org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsEnvSpec>().download = false
        }
        @Suppress("OPT_IN_USAGE")
        plugins.withType<org.jetbrains.kotlin.gradle.targets.wasm.binaryen.BinaryenPlugin> {
            the<org.jetbrains.kotlin.gradle.targets.wasm.binaryen.BinaryenEnvSpec>().download = false
        }
        project.plugins.withType<org.jetbrains.kotlin.gradle.targets.wasm.nodejs.WasmNodeJsPlugin> {
            the<org.jetbrains.kotlin.gradle.targets.wasm.nodejs.WasmNodeJsEnvSpec>().download = false
        }
    }
}

dependencies {
    subprojects.forEach {
        dokka(project(it.path))
    }
}
