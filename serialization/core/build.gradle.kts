import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

plugins {
    // not distributed as a package
    id("kmp-package")
}

group = "serialization"

kotlin {
    sourceSets {
        getByName("commonMain") {
            dependencies {
                api(project(":rdf"))
                api(project(":serialization:common"))
            }
        }
        getByName("commonTest") {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}

tasks.withType<KotlinJvmCompile> {
    compilerOptions {
        freeCompilerArgs.set(listOf("-Xadd-modules=jdk.incubator.vector"))
    }
}
