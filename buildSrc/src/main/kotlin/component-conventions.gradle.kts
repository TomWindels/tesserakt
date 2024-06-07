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
}
