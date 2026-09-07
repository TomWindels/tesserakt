package dev.tesserakt.rdf.types.impl

import dev.tesserakt.rdf.types.EncodedQuadElement
import dev.tesserakt.rdf.types.EncodingContext
import dev.tesserakt.rdf.types.MutableEncodingContext
import dev.tesserakt.rdf.types.Quad
import dev.tesserakt.util.SimpleList
import dev.tesserakt.util.getOrInsert
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.jvm.JvmInline

// TODO make internal
class TieredEncodingContextImpl internal constructor(
    // fallback for all non-named terms and named terms that do not adhere to the `http[s]://[www.]` prefix
    // TODO: can be further optimized in terms of memory:
    //  * literal term type URIs can also be represented using a similar lut, with an indicator bit, and the value in
    //   a different map
    //  * XSD-based typed literals can have their type encoded directly in the bit pattern
    //  * blank nodes could also be encoded using a special prefix, if we allow our context to only allow N distinct blank
    //  nodes
    private val fallback: MutableEncodingContext,
    // named nodes:
    //  protocol index > domain.tld > path > encoded value for that domain
    private val luts: Array<DomainCollection>,
    private val factory: () -> PathCollection,
): MutableEncodingContext {

    @OptIn(ExperimentalAtomicApi::class)
    internal data class DomainCollection(
        val lut: MutableMap<String, PathCollection>,
        val reverse: SimpleList<PathCollection>,
    ) {
        fun clear() {
            lut.clear()
            reverse.clear()
        }
    }

    @OptIn(ExperimentalAtomicApi::class)
    internal data class PathCollection(
        // domain "id", making up 10 bits (1024 distinct possible values)
        // initially unset as it is the result of the atomic insertion in the reverse lut
        var encoded: Int = -1,
        // TODO: make this configurable? v
        // paths associated with this specific domain value, making up the
        //  remaining 19 (32 - 10 - 4 - 1) bits (524 288 distinct possible values for any given domain)
        //  * 32-bit integers,
        //  * 12 bits spent on the domain ID
        //  * 2 bits on the 'header' (http[s]://[www.]
        //  * 1 bit on the sign (identifying that it is an encoded named term)
        val paths: MutableMap<String, Int>,
        // path ID -> path value
        val reverse: SimpleList<String>,
    )

    @JvmInline
    private value class CharArraySequence(val data: CharArray): CharSequence {
        override val length: Int
            get() = data.size

        override fun get(index: Int): Char {
            return data[index]
        }

        override fun subSequence(startIndex: Int, endIndex: Int): CharSequence {
            return data.concatToString(startIndex, endIndex)
        }

        override fun toString(): String {
            return data.concatToString()
        }
    }

    /**
     * Constructs a default tiered encoding context. The backing structures are **not** thread-safe!
     */
    @OptIn(ExperimentalAtomicApi::class)
    constructor(): this(
        fallback = MutableEncodingContextImpl(),
        luts = Array(4) { DomainCollection(HashMap(), SimpleList()) },
        factory = { PathCollection(paths = HashMap(), reverse = SimpleList()) },
    )

    override fun encode(element: Quad.Element): EncodedQuadElement {
        return when (element) {
            // keeping this as a special case
            // using MAX instead of 0 as 0 is emitted by the fallback
            Quad.DefaultGraph -> Int.MAX_VALUE
            is Quad.BlankTerm -> fallback.encode(element)
            is Quad.NamedTerm -> encodeNamedTerm(element.value)
            is Quad.LangString -> fallback.encode(element)
            is Quad.SimpleLiteral -> fallback.encode(element)
            is Quad.TypedLiteral -> fallback.encode(element)
        }
    }

    fun encodeNamed(value: CharArray): EncodedQuadElement {
        return encodeNamedTerm(CharArraySequence(value))
    }

    override fun decode(encoded: EncodedQuadElement): Quad.Element? {
        return when {
            encoded == Int.MAX_VALUE -> Quad.DefaultGraph
            encoded >= 0 -> fallback.decode(encoded)
            else -> {
                // a special pattern constructed by the named term encoder
                decodeNamedTerm(encoded)
            }
        }
    }

    override fun clear() {
        luts.forEach { it.clear() }
        fallback.clear()
    }

    override fun asReadOnlyEncodingContext(): EncodingContext {
        // shouldn't matter
        return this
    }

    private fun encodeNamedTerm(uri: CharSequence): EncodedQuadElement {
        val uriPrefixId = encodeUriPrefix(uri)
        if (uriPrefixId == -1) {
            return fallback.encode(Quad.NamedTerm(uri.toString()))
        }

        val lut = luts[uriPrefixId]
        val uriPrefixEnd = uriPrefix[uriPrefixId].length
        // mask is now either 0, 1, 2 or 3;
        //  we put it at the front of the encoded result (just after the sign bit indicating that this is an
        //  encoded named term
        val domainEnd = uri.indexOf('/', uriPrefixEnd)
        if (domainEnd == -1) {
            // special case, we cannot encode it properly as we enforce the presence of the `/` during reconstruction
            return fallback.encode(Quad.NamedTerm(uri.toString()))
        }
//        val domain = StringKey(str, uriPrefixEnd, domainEnd)
        val domain = uri.substring(uriPrefixEnd, domainEnd)
        // dropping the (first?) `/` value as that is inferred when concatenating the IDs
//        val path = StringKey(str, domainEnd + 1)
        val path = uri.substring(domainEnd + 1)

        val encodedDomain = lut.lut.getOrInsert(domain) {
            val entry = factory()
            val id = lut.reverse.add(entry)
            entry.encoded = id
            entry
        }

        val encodedPath = encodedDomain.paths.getOrInsert(path) {
            encodedDomain.reverse.add(path)
        }
        // TODO ensure domain and path are within the available bit range
        // constructing the complete encoded representation:
        // [sign: always 1] [uriPrefix: 2 bits] [domain: 10 bits] [encodedPath: 19 bits]
        return (1 shl 31) or (uriPrefixId shl 29) or ((encodedDomain.encoded and 1023) shl 19) or (encodedPath and 524_287)
    }

    /**
     * Returns the uriPrefix ID (0, 1, 2 or 3), or -1 if the input is prefix-incompatible
     */
    private fun encodeUriPrefix(input: CharSequence): Int {
        var i = 0
        http.forEach { c ->
            if (input.length <= i || input[i++] != c) {
                // unknown IRI uriPrefix, using fallback
                return -1
            }
        }
        if (input.length <= i) {
            return -1
        }
        // either we're looking at `s://` or `://`
        var uriPrefixId = 0
        val uriPrefixDivider = when (input[i++]) {
            's' -> {
                uriPrefixId = 1
                uriPrefixDivider1
            }
            ':' -> {
                // we keep mask at 0
                uriPrefixDivider2
            }
            else -> {
                return -1
            }
        }
        uriPrefixDivider.forEach { c ->
            if (input.length <= i || input[i++] != c) {
                return -1
            }
        }

        // we reached here; at this point, we can guarantee that we can encode this term properly
        // we can check for the presence of `www.` next
        www.forEach { c ->
            if (input.length <= i || input[i++] != c) {
                uriPrefixId = uriPrefixId or 2
                return@forEach
            }
        }
        return uriPrefixId
    }

    private fun decodeNamedTerm(value: EncodedQuadElement): Quad.NamedTerm {
        if (value and (1 shl 31) == 0) {
            throw IllegalArgumentException("Invalid ID - MSB is not 1: `${value}`")
        }
        val uriPrefixId = (value shr 29) and 3

        val lut = luts[uriPrefixId]

        val uriPrefix = uriPrefix[uriPrefixId]
        val encodedDomain = (value shr 19) and 1023
        // TODO set up and use reverse LUT
        val encodedDomainEntry = lut.lut.asIterable().find { it.value.encoded == encodedDomain }
            ?: throw IllegalArgumentException("Unknown domain ID: `${encodedDomain}`")
        // TODO: set up and use reverse LUT
        val encodedPath = (value and 524_287)
        val encodedPathEntry = encodedDomainEntry.value.paths.asIterable().find { it.value == encodedPath }
            ?: throw IllegalArgumentException("Unknown path ID: `${encodedPath}`")
        val uri = buildString {
            append(uriPrefix)
            append(encodedDomainEntry.key)
            append('/')
            append(encodedPathEntry.key)
        }
        return Quad.NamedTerm(uri)
    }

    companion object

}

private val uriPrefix = listOf("http://www.", "https://www.", "http://", "https://")
private val http = charArrayOf('h', 't', 't', 'p')
private val www = charArrayOf('w', 'w', 'w', '.')
private val uriPrefixDivider1 = charArrayOf(':', '/', '/')
private val uriPrefixDivider2 = charArrayOf('/', '/')

expect fun TieredEncodingContextImpl.Companion.concurrent(): TieredEncodingContextImpl
