import com.android.build.gradle.internal.tasks.factory.dependsOn

plugins {
    // expanding upon existing base components
    id("component-conventions")
    // making them publishable & buildable for android
    id("com.android.library")
}

repositories {
    mavenCentral()
    google()
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

kotlin {
    androidTarget {
        publishLibraryVariants("release", "debug")
    }
    sourceSets {
        // https://kotlinlang.org/docs/multiplatform-hierarchy.html#manual-configuration
        val nativeMain by creating {
            dependsOn(commonMain.get())
        }
        linuxX64Main.get().dependsOn(nativeMain)
        mingwX64Main.get().dependsOn(nativeMain)
        // the reason for this custom hierarchy:
        // https://slack-chats.kotlinlang.org/t/15994222/hello-hello-i-started-to-use-expected-actual-is-a-module-of-#735f0201-c023-485d-bc23-577addd2215c
        androidMain.get().dependsOn(jvmMain.get())
    }
    js {
        generateTypeScriptDefinitions()
        binaries.library()
    }
}

fun VersionCatalog.get(name: String): String = findVersion(name).orElseThrow().requiredVersion

// required to prevent "<task1> uses output of task <task2>" errors introduced when generating JS libraries
tasks.named("jsNodeProductionLibraryDistribution").dependsOn("jsTestTestDevelopmentExecutableCompileSync")
tasks.named("jsNodeTest").dependsOn("jsProductionLibraryCompileSync")

// as we're a package, and we have the JS source configured, we're creating an export task, that's collected by the
// js-build meta module
tasks.register("jsExport").dependsOn("jsProductionLibraryCompileSync")
