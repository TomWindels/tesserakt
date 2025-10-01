
import org.gradle.api.Project
import java.io.File
import java.util.*

fun Project.local(name: String): String? =
    this.properties[name]?.toString()?.takeIf { it.isNotBlank() } ?: runCatching {
        val properties = Properties()
        properties.load(File(rootDir.absolutePath + "/local.properties").inputStream())
        properties.getProperty(name, null)
    }.getOrNull()
