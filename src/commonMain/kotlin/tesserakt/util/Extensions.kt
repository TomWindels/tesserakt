@file:Suppress("NOTHING_TO_INLINE", "unused")

package tesserakt.util

inline fun String.fit(target: Int, overflow: String = "...", extend: String = " "): String =
    when {
        length < target ->
            this + extend.repeat((target - length / extend.length) + 1).substring(0, target - length)
        else ->
            truncate(maxLength = target, overflow = overflow)
    }

inline fun String.truncate(maxLength: Int, overflow: String = "..."): String =
    when {
        length <= maxLength -> this
        maxLength <= overflow.length -> overflow.substring(0, maxLength)
        else -> substring(0, maxLength - overflow.length) + overflow
    }

inline fun Any?.toTruncatedString(maxLength: Int, overflow: String = "..."): String {
    return toString().truncate(maxLength, overflow)
}
