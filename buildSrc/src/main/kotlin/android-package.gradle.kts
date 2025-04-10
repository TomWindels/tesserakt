plugins {
    id("jvm-package")
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
        publishLibraryVariants("release", "debug")
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
    namespace = "dev.tesserakt"
    compileOptions {
        // removeFirst was only added to java.util.List in JDK 21
        // https://github.com/javalin/javalin/issues/2117#issuecomment-1960114620
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

fun VersionCatalog.get(name: String): String = findVersion(name).orElseThrow().requiredVersion
