plugins {
    // not distributed as a package
    id("kmp-package")
}

group = "serialization"

kotlin {
    sourceSets {
        getByName("commonMain") {
            dependencies {
                implementation(project(":utils"))
                implementation(project(":serialization:core"))
                api(project(":serialization:common"))
                api(project(":rdf"))
            }
        }
        getByName("commonTest") {
            dependencies {
                implementation(project(":rdf:dsl"))
                implementation(project(":testing:tooling:environment"))
                implementation(kotlin("test"))
            }
        }
        getByName("jvmTest") {
            dependencies {
                implementation(kotlin("test"))
                implementation(project(":utils"))
                implementation(project(":interop:jena"))
                implementation(project(":testing:tooling:environment"))
                implementation(libs.kotlinx.coroutines.core)
            }
        }
        getByName("jsTest") {
            dependencies {
                implementation(libs.kotlinx.coroutines.test)
            }
        }
    }
}

tasks.jvmTest {
    javaLauncher.set(
        javaToolchains.launcherFor {
            languageVersion = JavaLanguageVersion.of(21)
        }
    )
}

// we want to target the JavaCompile task for the tests specifically, as those
//  also need JVM >= 21 to function for a functional Jena (w/ its transitive dependencies) setup
tasks.withType(JavaCompile::class.java) {
    if (!name.contains("jvmTest", ignoreCase = true)) {
        return@withType
    }
    sourceCompatibility = "21"
    targetCompatibility = "21"
}
