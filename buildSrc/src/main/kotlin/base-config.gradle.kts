plugins {
    kotlin("multiplatform")
}

kotlin {
    // compiler configuration
    // silencing expect/actual warnings
    targets.all {
        compilations.all {
            compileTaskProvider.configure {
                compilerOptions {
                    freeCompilerArgs.add("-Xexpect-actual-classes")
                    freeCompilerArgs.add("-Xsuppress-warning=NOTHING_TO_INLINE")
                }
            }
        }
    }
}

val StableSemVer = Regex("[0-9]+(?:\\.[0-9]+)+")
val CurrentVersion = project.property("VERSION_NAME") as String
val IsUnstableVersion = !CurrentVersion.matches(StableSemVer)
val IsBuildRequest = gradle.startParameter.taskRequests.any { it.args.any { it.contains("build", ignoreCase = true) } }
// only enforcing tests when not building, or when building an unstable version
val TestsEnforced = !IsBuildRequest || !IsUnstableVersion

println("Configuring ${project.name} v${CurrentVersion} (${if (IsUnstableVersion) "unstable" else "stable"})")
if (!TestsEnforced) {
    println("Unstable build request detected. Not enforcing tests.")
} else {
    println("Tests are enabled.")
}

// setting the flag on the most basic abstraction of tests, once all tests for a given task become available
gradle.projectsEvaluated {
    tasks.withType(AbstractTestTask::class.java) {
        if (!TestsEnforced) {
            println("Ignoring failures from ${this.project.path}:$name!")
        }
        ignoreFailures = !TestsEnforced
    }
    // and the generated reports also tend to fail as a result
    tasks.withType(TestReport::class.java) {
        if (!TestsEnforced) {
            println("Disabling reports from ${this.project.path}:$name!")
        }
        enabled = TestsEnforced
    }
}
