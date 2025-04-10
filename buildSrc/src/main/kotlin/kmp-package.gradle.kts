plugins {
    // we're also a js package, which already manages versions & tests
    id("js-package")
    kotlin("multiplatform")
    // making them publishable & buildable for android
    id("com.android.library")
}

repositories {
    mavenCentral()
    google()
}

kotlin {
    // target configuration
    jvm()
    // js is already configured
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
