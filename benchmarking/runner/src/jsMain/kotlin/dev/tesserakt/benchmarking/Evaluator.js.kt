package dev.tesserakt.benchmarking

actual val references: Map<String, (String) -> Reference> = mapOf(
    "comunica" to { ComunicaReference(it) }
)
