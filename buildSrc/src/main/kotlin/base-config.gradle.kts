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

val configVersion = project.property("VERSION_NAME") as String
val stableSemVer = Regex("[0-9]+(?:\\.[0-9]+)+")
require(configVersion matches stableSemVer) {
    "Invalid version configured! Only regular versions (semver) are allowed!"
}
// checking the environment "TARGET" variable to see whether we're built from a release (tag) action or not;
//  non-release build tasks retain the `-SNAPSHOT` logic, so manual publish actions are automatically released
//  to snapshot maven repos
val isSnapshot = System.getenv("TARGET") != "release"
version = if (isSnapshot) {
    "$configVersion-SNAPSHOT"
} else {
    configVersion
}
val IsBuildRequest = gradle.startParameter.taskRequests.any { it.args.any { it.contains("build", ignoreCase = true) } }
// only enforcing tests when not building, or when building an unstable version
val TestsEnforced = !IsBuildRequest || !isSnapshot

println("Configuring ${project.name} v${version} (${if (isSnapshot) "unstable" else "stable"})")
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
