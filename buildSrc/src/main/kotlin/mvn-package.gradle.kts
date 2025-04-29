import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinMultiplatform
import com.vanniktech.maven.publish.SonatypeHost

plugins {
    id("base-config")
    id("com.vanniktech.maven.publish.base")
}

mavenPublishing {
    val artifactId = getArtifactId()
    coordinates(project.property("MAVEN_CENTRAL_GROUP_ID") as String, "tesserakt-$artifactId", version = project.version as String)

    publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL)

    // src: https://vanniktech.github.io/gradle-maven-publish-plugin/what/
    configure(KotlinMultiplatform(
        // configures the -javadoc artifact, possible values:
        // - `JavadocJar.None()` don't publish this artifact
        // - `JavadocJar.Empty()` publish an emprt jar
        // - `JavadocJar.Javadoc()` to publish standard javadocs
        javadocJar = JavadocJar.Empty(),
        // whether to publish a sources jar
        sourcesJar = true,
        // configure which Android library variants to publish if this project has an Android target
        // defaults to "release" when using the main plugin and nothing for the base plugin
        androidVariantsToPublish = if (SNAPSHOT) listOf("debug") else listOf("release"),
    ))

    pom {
        name.set("tesserakt ($artifactId)")
        description.set(project.property("POM_DESCRIPTION") as String)
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
        scm {
            url.set(project.property("POM_SCM_URL") as String)
            connection.set(project.property("POM_SCM_CONNECTION") as String)
            developerConnection.set(project.property("POM_SCM_DEV_CONNECTION") as String)
        }
    }

    if (!SNAPSHOT) {
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
