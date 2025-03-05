plugins {
    // not directly distributed as a package
    kotlin("multiplatform")
}

kotlin {
    js {
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
            val jsMain by getting {
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
