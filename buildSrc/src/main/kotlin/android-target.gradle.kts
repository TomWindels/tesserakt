plugins {
    id("jvm-target")
    // making them publishable & buildable for android
    id("com.android.kotlin.multiplatform.library")
    id("com.android.lint")
}

repositories {
    mavenCentral()
    google()
}

kotlin {
    androidLibrary {
        val libs = versionCatalogs.named("libs")
        compileSdk = libs.get("compileSdk").toInt()
        namespace = getNamespace()
        compilations.configureEach {
            // the suggested change makes the JVM-specific compiler options unavailable as the compiler
            //  task only provides the compiler options for the common sourceset...
            @Suppress("DEPRECATION")
            compilerOptions.configure {
                jvmTarget.set(
                    org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_1_8
                )
            }
        }
        lint {
            abortOnError = true
            enable += listOf("NewApi", "InvalidPackage", "NewerVersionAvailable", "NoOp", "StopShip", "SyntheticAccessor")
            warning += listOf("StopShip")
            fatal += listOf("NewApi", "InvalidPackage")
        }
        println("Configured Android Library $namespace")
    }

    // source set configuration
    sourceSets {
        // the reason for this custom hierarchy:
        // https://slack-chats.kotlinlang.org/t/15994222/hello-hello-i-started-to-use-expected-actual-is-a-module-of-#735f0201-c023-485d-bc23-577addd2215c
        androidMain.get().dependsOn(sourceSets.named("commonJvmMain").get())
    }
}

fun getNamespace(): String {
    var name = project.name.replace("-", "_")
    var parent = project.parent?.takeIf { it != project.rootProject }
    while (parent != null) {
        val current = parent.name.replace("-", "_")
        name = "$current.$name"
        parent = parent.parent?.takeIf { it != project.rootProject }
    }
    return "dev.tesserakt.$name"
}

fun VersionCatalog.get(name: String): String = findVersion(name).orElseThrow().requiredVersion
