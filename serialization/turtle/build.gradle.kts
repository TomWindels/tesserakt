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



// src: https://slack-chats.kotlinlang.org/t/486856/anyone-knows-how-to-create-gradle-javaexec-configuration-for#20242df1-da93-4272-8f2e-168a8891a398
val jvmJar = tasks.named("jvmJar")
val jvmRuntimeClasspath = configurations.named("jvmRuntimeClasspath")

val runTurtleTest = tasks.register("runTurtleTest", JavaExec::class) {
    group = "verification"
    mainClass.set("TestKt")
    this.environment["INPUT_FILE"] = "path/to/data.nt"

    // required for the vector API to be available
    jvmArgs("--add-modules=jdk.incubator.vector")
    // alternatively, all these can be enabled to see the compiled result
//    jvmArgs(
//        "--add-modules=jdk.incubator.vector",
//        // from https://jornvernee.github.io/hotspot/jit/2023/08/18/debugging-jit.html
//        "-Xbatch",
//        "-XX:+UnlockDiagnosticVMOptions",
//        "-XX:-TieredCompilation",
//        "-XX:PrintAssemblyOptions=intel",
//        "-XX:CompileCommand=dontinline,dev.tesserakt.rdf.serialization.util.FileCharStream::findWhitespaceEscapeSequenceOr",
//        "-XX:CompileCommand=print,dev.tesserakt.rdf.serialization.util.FileCharStream::findWhitespaceEscapeSequenceOr",
//    )

    classpath(jvmJar, jvmRuntimeClasspath)
}
