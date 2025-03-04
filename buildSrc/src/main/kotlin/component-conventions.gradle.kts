import org.jetbrains.kotlin.gradle.targets.js.testing.KotlinJsTest

plugins {
    kotlin("multiplatform")
}

repositories {
    mavenCentral()
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

// setting the flag on the most basic abstraction of tests known, some
//  Kotlin tests **do not** extend the regular Test task base type
tasks.withType(AbstractTestTask::class.java) {
    ignoreFailures = !TestsEnforced
}
// some other Kotlin tests, such as the NodeJS variants, still halt the pipeline
//  upon failure, so these are disabled entirely
tasks.withType(KotlinJsTest::class.java) {
    enabled = TestsEnforced
}
// and the generated reports also tend to fail as a result
tasks.withType(TestReport::class.java) {
    enabled = TestsEnforced
}

// FIXME doesnt work
tasks.findByName("kotlinStoreYarnLock")?.apply {
    enabled = false
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
