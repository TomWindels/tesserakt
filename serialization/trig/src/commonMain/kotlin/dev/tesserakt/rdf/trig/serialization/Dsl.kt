package dev.tesserakt.rdf.trig.serialization

import dev.tesserakt.rdf.ontology.Ontology
import dev.tesserakt.rdf.serialization.common.Prefixes
import dev.tesserakt.rdf.trig.serialization.TRiGConfig.PrettyFormatterConf


@TrigSerializerDsl
fun trig(builder: TRiGConfig.() -> Unit): TriGSerializer {
    val config = TRiGConfig().apply(builder)
    return TriGSerializer(config = config)
}

inline fun TRiGConfig.prettyFormatting(block: PrettyFormatterConf.() -> Unit = {}) {
    formatter = PrettyFormatterConf().apply(block).build()
}

inline fun TRiGConfig.simpleFormatting() {
    formatter = SimpleFormatter
}

fun PrettyFormatterConf.prefixes(value: Prefixes) {
    this.prefixes = value
}

fun PrettyFormatterConf.prefixes(vararg ontology: Ontology) {
    this.prefixes = Prefixes(*ontology)
}

fun PrettyFormatterConf.prefixes(value: Map<String, String>) {
    this.prefixes = Prefixes(value)
}

fun PrettyFormatterConf.prefixes(value: Iterable<Pair<String, String>>) {
    this.prefixes = Prefixes(value.toMap())
}

inline fun PrettyFormatterConf.prefixes(block: MutableMap<String, String>.() -> Unit) {
    prefixes(buildMap(block))
}