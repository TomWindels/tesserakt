package sparql.tests

import dev.tesserakt.rdf.serialization.NTriples.decodeSingleLine
import dev.tesserakt.rdf.types.Store
import java.io.File

internal actual fun readFile(filepath: String): Result<Store> = runCatching {
    when {
        filepath.endsWith(".nt") -> {
            Store()
                .also { store -> File(filepath).forEachLine { statement -> store.add(statement.decodeSingleLine()) } }
        }

        else -> {
            throw IllegalArgumentException("Unknown filetype: `${filepath.substringAfterLast('.')}`")
        }
    }
}
