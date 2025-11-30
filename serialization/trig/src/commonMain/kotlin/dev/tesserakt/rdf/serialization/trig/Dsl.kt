package dev.tesserakt.rdf.serialization.trig

import dev.tesserakt.rdf.ontology.Ontology
import dev.tesserakt.rdf.serialization.common.Prefixes
import dev.tesserakt.rdf.types.Quad


inline fun TriGConfig.setBase(iri: String) {
    base = iri
}

inline fun TriGConfig.setBase(term: Quad.NamedTerm) {
    setBase(iri = term.value)
}

inline fun TriGConfig.usePrettyFormatting(block: TriGConfig.PrettyFormatterConf.() -> Unit = {}) {
    formatter = TriGConfig.PrettyFormatterConf().apply(block).build()
}

inline fun TriGConfig.useSimpleFormatting() {
    formatter = SimpleTriGFormatter
}

/**
 * Use the prefixes [value]. Overrides any prior call to [withPrefixes]
 */
fun TriGConfig.PrettyFormatterConf.withPrefixes(value: Prefixes) {
    this.prefixes = value
}

/**
 * Use the prefixes of the [ontology]. Overrides any prior call to [withPrefixes].
 */
fun TriGConfig.PrettyFormatterConf.withPrefixes(vararg ontology: Ontology) {
    this.prefixes = Prefixes(*ontology)
}

/**
 * Use the prefixes inside of [value]. Overrides any prior call to [withPrefixes]
 */
fun TriGConfig.PrettyFormatterConf.withPrefixes(value: Map<String, String>) {
    this.prefixes = Prefixes(value)
}

/**
 * Use the prefixes inside of [value]. Overrides any prior call to [withPrefixes]
 */
fun TriGConfig.PrettyFormatterConf.withPrefixes(value: Iterable<Pair<String, String>>) {
    this.prefixes = Prefixes(value.toMap())
}

/**
 * Use the prefixes inserted in the [block]. The scope behaves as a map builder, see methods such as [buildMap].
 *  Overrides any prior call to [withPrefixes]
 */
inline fun TriGConfig.PrettyFormatterConf.withPrefixes(block: MutableMap<String, String>.() -> Unit) {
    withPrefixes(buildMap(block))
}

/**
 * Apply a fixed indent: the indent for new lines is fixed in length, depending on the depth of the preceding
 *  content (i.e., a single occurrence when in a graph, two occurrences when in a graph and repeated subject, etc.).
 *
 * This is the default indent type.
 */
fun TriGConfig.PrettyFormatterConf.withFixedIndent(pattern: String = INDENT_PATTERN) {
    this.indent = PrettyTriGFormatter.FixedStepIndent(pattern = pattern)
}

/**
 * Apply a dynamic indent: the indent for new lines is depending on the length of the preceding content
 */
fun TriGConfig.PrettyFormatterConf.withDynamicIndent(pattern: String = INDENT_PATTERN) {
    this.indent = PrettyTriGFormatter.DynamicIndent(pattern = pattern)
}
