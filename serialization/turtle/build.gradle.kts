plugins {
    // not distributed as a package
    id("kmp-package")
}

group = "serialization"

kotlin {
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(project(":utils"))
                implementation(project(":serialization:core"))
                api(project(":serialization:common"))
                api(project(":rdf"))
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(project(":rdf:dsl"))
                implementation(project(":testing:tooling:environment"))
                implementation(kotlin("test"))
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(project(":utils"))
                implementation(project(":interop:jena"))
                implementation(project(":testing:tooling:environment"))
                implementation(libs.kotlinx.coroutines.core)
            }
        }
        val jsTest by getting {
            dependencies {
                implementation(libs.kotlinx.coroutines.test)
            }
        }
    }
}

tasks.jvmTest {
    javaLauncher.set(
        javaToolchains.launcherFor {
            languageVersion = JavaLanguageVersion.of(17)
        }
    )
}

// we want to target the JavaCompile task for the tests specifically, as those
//  also need JVM >= 17 to function for a functional Jena (w/ its transitive dependencies) setup
tasks.withType(JavaCompile::class.java) {
    if (!name.contains("jvmTest", ignoreCase = true)) {
        return@withType
    }
    sourceCompatibility = "17"
    targetCompatibility = "17"
}
