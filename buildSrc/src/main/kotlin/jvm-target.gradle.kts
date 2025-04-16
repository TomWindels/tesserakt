plugins {
    id("base-config")
}

repositories {
    mavenCentral()
}

kotlin {
    // target configuration
    jvm()

    // source set configuration
    sourceSets {
        // https://kotlinlang.org/docs/multiplatform-hierarchy.html#manual-configuration
        // the reason for this custom hierarchy:
        // https://slack-chats.kotlinlang.org/t/15994222/hello-hello-i-started-to-use-expected-actual-is-a-module-of-#735f0201-c023-485d-bc23-577addd2215c
        val commonJvmMain by creating {
            dependsOn(commonMain.get())
        }
        jvmMain.get().dependsOn(commonJvmMain)
        // in case of an android target, this can now also depend on the `commonJvmMain` sourceset
    }
}
