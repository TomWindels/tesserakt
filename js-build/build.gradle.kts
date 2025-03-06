plugins {
    // not directly distributed as a package
    kotlin("multiplatform")
}

kotlin {
    wasmJs {
        compilerOptions {
            freeCompilerArgs.add("-Xwasm-use-new-exception-proposal")
        }
        nodejs()
        generateTypeScriptDefinitions()
        binaries.library()
    }
}

// not exporting with any group here
group = ""

gradle.projectsEvaluated {
    kotlin {
        sourceSets {
            val wasmJsMain by getting {
                dependencies {
                    (rootProject.subprojects - project)
                        .filter { it.tasks.findByName("jsExport") != null }
                        .also { println("The JS build contains the following modules: ${it.joinToString { it.path }}") }
                        .forEach { subproject -> api(subproject) }
                }
            }
        }
    }
}
