plugins {
    kotlin("multiplatform")
}

repositories {
    mavenCentral()
}

kotlin {
    jvm()
    js {
        nodejs()
    }
    mingwX64()
    linuxX64()

    // silencing expect/actual warnings
    targets.all {
        compilations.all {
            compileTaskProvider.configure {
                compilerOptions {
                    freeCompilerArgs.add("-Xexpect-actual-classes")
                }
            }
        }
    }
}
