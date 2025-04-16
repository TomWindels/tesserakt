plugins {
    id("base-config")
}

repositories {
    mavenCentral()
}

kotlin {
    mingwX64()
    linuxX64()

    // source set configuration
    sourceSets {
        // https://kotlinlang.org/docs/multiplatform-hierarchy.html#manual-configuration
        val nativeMain by creating {
            dependsOn(commonMain.get())
        }
        linuxX64Main.get().dependsOn(nativeMain)
        mingwX64Main.get().dependsOn(nativeMain)
    }
}
