import com.vanniktech.maven.publish.SonatypeHost

plugins {
    id("base-config")
    id("com.vanniktech.maven.publish.base")
}

mavenPublishing {
    coordinates(project.property("MAVEN_CENTRAL_GROUP_ID") as String, getArtifactId(), version = project.version as String)
    publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL)
    println("Configured Maven package ${project.property("MAVEN_CENTRAL_GROUP_ID") as String}:${getArtifactId()}:${project.version as String}")
}

fun getArtifactId(): String {
    var name = project.name.replace("-", "_")
    var parent = project.parent?.takeIf { it != project.rootProject }
    while (parent != null) {
        val current = parent.name.replace("-", "_")
        name = "$current-$name"
        parent = parent.parent?.takeIf { it != project.rootProject }
    }
    return "tesserakt-$name"
}
