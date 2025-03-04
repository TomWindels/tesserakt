plugins {
    kotlin("multiplatform")
}

repositories {
    mavenCentral()
}

kotlin {
    jvm()
    js {
        var name = project.name
        var parent = project.parent?.takeIf { it != project.rootProject }
        while (parent != null) {
            name = "${parent.name}-$name"
            parent = parent.parent?.takeIf { it != project.rootProject }
        }
        compilations.forEach {
            // setting the outputModuleName to the entire name value (= incl. the scope) yields
            //  runtime exceptions, so that approach is avoided
            // src for this approach:
            // https://youtrack.jetbrains.com/issue/KT-25878/Provide-Option-to-Define-Scoped-NPM-Package#focus=Comments-27-6742844.0-0
            it.packageJson {
                customField("name", "@${project.findProperty("NPM_ORGANISATION")}/${name}")
            }
            it.outputModuleName = name
            println("Configured NPM package ${project.findProperty("NPM_ORGANISATION")}/${name}")
        }
        nodejs()
    }
    mingwX64()
    linuxX64()

    // silencing expect/actual warnings
    targets.all {
        compilations.all {
            compileTaskProvider.configure {
                compilerOptions {
                    freeCompilerArgs.add("-Xexpect-actual-classes")
                }
            }
        }
    }
}
