plugins {
    id("jvm-target")
    // making them publishable & buildable for android
    id("com.android.library")
}

repositories {
    mavenCentral()
    google()
}

kotlin {
    // target configuration
    androidTarget {
        when {
            (version as String).endsWith("-SNAPSHOT") -> {
                publishLibraryVariants("debug")
            }
            else -> {
                publishLibraryVariants("release")
            }
        }
    }

    // source set configuration
    sourceSets {
        // the reason for this custom hierarchy:
        // https://slack-chats.kotlinlang.org/t/15994222/hello-hello-i-started-to-use-expected-actual-is-a-module-of-#735f0201-c023-485d-bc23-577addd2215c
        androidMain.get().dependsOn(sourceSets.named("commonJvmMain").get())
    }
}

android {
    val libs = versionCatalogs.named("libs")
    compileSdk = libs.get("compileSdk").toInt()
    namespace = getNamespace()
    println("Configured Android Library $namespace")
    compileOptions {
        // removeFirst was only added to java.util.List in JDK 21
        // https://github.com/javalin/javalin/issues/2117#issuecomment-1960114620
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
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
