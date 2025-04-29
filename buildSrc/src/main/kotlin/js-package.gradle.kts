
import com.android.build.gradle.internal.tasks.factory.dependsOn
import org.jetbrains.kotlin.gradle.targets.js.yarn.YarnLockMismatchReport
import org.jetbrains.kotlin.gradle.targets.js.yarn.YarnPlugin
import org.jetbrains.kotlin.gradle.targets.js.yarn.YarnRootExtension

plugins {
    id("base-config")
    // for Kotlin/JS consumers
    id("mvn-package")
}

repositories {
    mavenCentral()
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
    js {
        var name = project.name
        var parent = project.parent?.takeIf { it != project.rootProject }
        while (parent != null) {
            name = "${parent.name}-$name"
            parent = parent.parent?.takeIf { it != project.rootProject }
        }
        moduleName = name
        val npmPackageName = "@${project.findProperty("NPM_ORGANISATION")}/${name}"
        compilations.forEach { compilation ->
            // setting the outputModuleName to the entire name value (= incl. the scope) yields
            //  runtime exceptions, so that approach is avoided
            // src for this approach:
            // https://youtrack.jetbrains.com/issue/KT-25878/Provide-Option-to-Define-Scoped-NPM-Package#focus=Comments-27-6742844.0-0
            compilation.packageJson {
                customField("name", npmPackageName)
            }
            compilation.outputModuleName = name
        }
        println("Configured NPM package $npmPackageName")
        nodejs()
        generateTypeScriptDefinitions()
        binaries.library()

        // snapshot releases should not have minimised member names to help with debugging
        if (SNAPSHOT) {
            compilations.all {
                compileTaskProvider.configure {
                    compilerOptions.freeCompilerArgs.add("-Xir-minimized-member-names=false")
                }
            }
        }
    }

    // no custom source set configuration

    // no custom compiler configuration
}

// required to prevent "<task1> uses output of task <task2>" errors introduced when generating JS libraries
tasks.named("jsNodeProductionLibraryDistribution").dependsOn("jsTestTestDevelopmentExecutableCompileSync")
tasks.named("jsNodeTest").dependsOn("jsProductionLibraryCompileSync")

// as we're a package, and we have the JS source configured, we're creating an export task, that's collected by the
// js-build meta module
tasks.register("jsExport").dependsOn("jsProductionLibraryCompileSync")
