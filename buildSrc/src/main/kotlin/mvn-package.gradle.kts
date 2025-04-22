import com.vanniktech.maven.publish.SonatypeHost

plugins {
    id("base-config")
    id("com.vanniktech.maven.publish.base")
}

mavenPublishing {
    val artifactId = getArtifactId()
    coordinates(project.property("MAVEN_CENTRAL_GROUP_ID") as String, "tesserakt-$artifactId", version = project.version as String)
    publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL)

    pom {
        name.set("tesserakt ($artifactId)")
        inceptionYear.set("2023")
        url.set(project.property("GIT_URL") as String)
        licenses {
            license {
                name.set("The MIT License")
                url.set("https://opensource.org/license/MIT")
            }
        }
        developers {
            developer {
                id.set("TomWindels")
                name.set("Tom Windels")
                url.set("https://github.com/TomWindels/")
            }
        }
    }

    if (!(version as String).endsWith("-SNAPSHOT")) {
        signAllPublications()
    }

    println("Configured Maven package ${project.property("MAVEN_CENTRAL_GROUP_ID") as String}:tesserakt-${artifactId}:${project.version as String}")
}

fun getArtifactId(): String {
    var name = project.name.replace("-", "_")
    var parent = project.parent?.takeIf { it != project.rootProject }
    while (parent != null) {
        val current = parent.name.replace("-", "_")
        name = "$current-$name"
        parent = parent.parent?.takeIf { it != project.rootProject }
    }
    return name
}
