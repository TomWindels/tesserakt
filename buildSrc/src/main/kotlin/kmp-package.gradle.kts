
import com.android.build.gradle.internal.tasks.factory.dependsOn
import org.jetbrains.kotlin.gradle.targets.js.yarn.YarnLockMismatchReport
import org.jetbrains.kotlin.gradle.targets.js.yarn.YarnPlugin
import org.jetbrains.kotlin.gradle.targets.js.yarn.YarnRootExtension

plugins {
    kotlin("multiplatform")
    // making them publishable & buildable for android
    id("com.android.library")
}

repositories {
    mavenCentral()
    google()
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

// ensuring the yarn lock file behaves
// src: https://kotlinlang.org/docs/whatsnew18.html#new-settings-for-reporting-that-yarn-lock-has-been-updated
rootProject.plugins.withType(YarnPlugin::class.java) {
    rootProject.the<YarnRootExtension>().yarnLockMismatchReport = YarnLockMismatchReport.NONE
    rootProject.the<YarnRootExtension>().reportNewYarnLock = true
    rootProject.the<YarnRootExtension>().yarnLockAutoReplace = true
}

kotlin {
    // target configuration
    jvm()
    js {
        var name = project.name
        var parent = project.parent?.takeIf { it != project.rootProject }
        while (parent != null) {
            name = "${parent.name}-$name"
            parent = parent.parent?.takeIf { it != project.rootProject }
        }
        compilations.forEach { compilation ->
            // setting the outputModuleName to the entire name value (= incl. the scope) yields
            //  runtime exceptions, so that approach is avoided
            // src for this approach:
            // https://youtrack.jetbrains.com/issue/KT-25878/Provide-Option-to-Define-Scoped-NPM-Package#focus=Comments-27-6742844.0-0
            val npmPackageName = "@${project.findProperty("NPM_ORGANISATION")}/${name}"
            compilation.packageJson {
                customField("name", npmPackageName)
            }
            compilation.outputModuleName = name
            if (compilation.name != "test") {
                println("Configured NPM package $npmPackageName")
            }
        }
        nodejs()
        browser()
        generateTypeScriptDefinitions()
        binaries.library()
    }
    mingwX64()
    linuxX64()
    androidTarget {
        publishLibraryVariants("release", "debug")
    }

    // source set configuration
    sourceSets {
        // https://kotlinlang.org/docs/multiplatform-hierarchy.html#manual-configuration
        val nativeMain by creating {
            dependsOn(commonMain.get())
        }
        linuxX64Main.get().dependsOn(nativeMain)
        mingwX64Main.get().dependsOn(nativeMain)
        // the reason for this custom hierarchy:
        // https://slack-chats.kotlinlang.org/t/15994222/hello-hello-i-started-to-use-expected-actual-is-a-module-of-#735f0201-c023-485d-bc23-577addd2215c
        val commonJvmMain by creating {
            dependsOn(commonMain.get())
        }
        jvmMain.get().dependsOn(commonJvmMain)
        androidMain.get().dependsOn(commonJvmMain)
    }

    // compiler configuration
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

android {
    val libs = versionCatalogs.named("libs")
    compileSdk = libs.get("compileSdk").toInt()
    namespace = "dev.tesserakt"
    compileOptions {
        // removeFirst was only added to java.util.List in JDK 21
        // https://github.com/javalin/javalin/issues/2117#issuecomment-1960114620
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

fun VersionCatalog.get(name: String): String = findVersion(name).orElseThrow().requiredVersion

// required to prevent "<task1> uses output of task <task2>" errors introduced when generating JS libraries
tasks.named("jsNodeProductionLibraryDistribution").dependsOn("jsTestTestDevelopmentExecutableCompileSync")
tasks.named("jsNodeTest").dependsOn("jsProductionLibraryCompileSync")

// as we're a package, and we have the JS source configured, we're creating an export task, that's collected by the
// js-build meta module
tasks.register("jsExport").dependsOn("jsProductionLibraryCompileSync")
