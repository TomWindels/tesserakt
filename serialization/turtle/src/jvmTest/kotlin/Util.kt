
import dev.tesserakt.interop.jena.toQuad
import dev.tesserakt.rdf.types.Quad
import org.apache.jena.riot.Lang
import org.apache.jena.riot.RDFParser
import java.util.stream.Collectors
import kotlin.io.path.Path
import kotlin.io.path.isRegularFile
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.pathString

internal fun externalTurtleFileParser(filepath: String): List<Quad> {
    val parser = RDFParser
        .create()
        .source(Path(filepath))
        .lang(Lang.TURTLE)
        .build()
    return parser.toGraph().stream().map { it.toQuad() }.collect(Collectors.toList())
}

internal fun externalTurtleTextParser(text: String): List<Quad> {
    val parser = RDFParser
        .create()
        .fromString(text)
        .lang(Lang.TURTLE)
        .build()
    return parser.toGraph().stream().map { it.toQuad() }.collect(Collectors.toList())
}

internal fun listFiles(path: String): List<String> = Path(path)
    .listDirectoryEntries()
    .mapNotNull { entry -> entry.pathString.takeIf { entry.isRegularFile() } }
