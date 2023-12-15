@file:Suppress("NOTHING_TO_INLINE")

package tesserakt.util

object Unicode {

    const val ERR = "\u001b[31m"
    const val BOLD = "\u001b[1m"

    const val END = "\u001b[0m"

    inline fun String.bold() = "$BOLD$this$END"

}
