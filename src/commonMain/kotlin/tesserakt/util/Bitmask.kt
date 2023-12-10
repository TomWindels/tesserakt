package tesserakt.util

import kotlin.jvm.JvmInline

@JvmInline
internal value class Bitmask(private val bits: UInt) {

    operator fun get(index: Int) = ((bits shr index) and 1u) == 1u

    companion object {

        fun from(vararg boolean: Boolean) =
            create(boolean.iterator())

        fun from(booleans: Iterable<Boolean>) =
            create(booleans.iterator())

        /* helper */

        private fun create(iterator: Iterator<Boolean>): Bitmask {
            var result = 0u
            var offset = 0
            while (iterator.hasNext()) {
                result = result or ((if (iterator.next()) 1u else 0u) shl offset++)
            }
            return Bitmask(bits = result)
        }

    }

}
