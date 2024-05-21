plugins {
    kotlin("multiplatform")
}

repositories {
    mavenCentral()
}

kotlin {
    jvm {
        jvmToolchain(8)
    }
    js {
        nodejs()
    }
    mingwX64()
    linuxX64()
}
