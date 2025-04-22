
import org.gradle.api.Project
import java.io.File
import java.util.*

fun Project.local(name: String): String? =
    runCatching {
        val properties = Properties()
        properties.load(File(rootDir.absolutePath + "/local.properties").inputStream())
        return properties.getProperty(name, null)
    }.getOrNull()

fun Project.hasEnabled(name: String): Boolean {
    val value = local(name)?.lowercase() ?: return false
    return value in setOf("true", "enabled")
}
