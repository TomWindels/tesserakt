plugins {
    // expanding upon existing base components
    id("component-conventions")
    // making them publishable & buildable for android
    id("com.android.library")
}

android {
    val libs = versionCatalogs.named("libs")
    compileSdk = libs.get("compileSdk").toInt()
    namespace = "dev.tesserakt"
}

kotlin {
    androidTarget {
        publishLibraryVariants("release", "debug")
    }
}

fun VersionCatalog.get(name: String): String = findVersion(name).orElseThrow().requiredVersion
