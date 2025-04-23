import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

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

// preventing `NoSuchMethodException`s from occurring when dealing with Compile SDK 35 and above on Android when
//  using extension functions such as `MutableList::removeFirst()`
// src: https://jakewharton.com/kotlins-jdk-release-compatibility-flag/

tasks.withType(JavaCompile::class.java).configureEach {
    sourceCompatibility = JavaVersion.VERSION_1_8.name
    targetCompatibility = JavaVersion.VERSION_1_8.name
}

tasks.withType(KotlinJvmCompile::class.java).configureEach {
    compilerOptions.jvmTarget = JvmTarget.JVM_1_8
    compilerOptions.freeCompilerArgs.add("-Xjdk-release=1.8")
}
