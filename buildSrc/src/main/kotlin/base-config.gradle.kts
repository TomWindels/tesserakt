plugins {
    kotlin("multiplatform")
}

kotlin {
    // compiler configuration
    // silencing expect/actual warnings
    targets.all {
        compilations.all {
            compileTaskProvider.configure {
                compilerOptions {
                    freeCompilerArgs.add("-Xexpect-actual-classes")
                    freeCompilerArgs.add("-Xsuppress-warning=NOTHING_TO_INLINE")
                }
            }
        }
    }
}
