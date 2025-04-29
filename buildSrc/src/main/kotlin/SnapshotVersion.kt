import org.gradle.api.Project

val Project.SNAPSHOT: Boolean
    get() = (version as String).endsWith("-SNAPSHOT")
