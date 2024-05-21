repositories {
    mavenCentral()
}

buildscript {
    allprojects {
        group = "dev.tesserakt"
        version = project.property("VERSION_NAME") as String
    }
}
