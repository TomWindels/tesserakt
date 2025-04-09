package dev.tesserakt.util


inline fun <reified T> Any.jsCastOrBail(): T {
    return this as? T ?: throw Error("Invalid type: ${this::class.simpleName}\nExpected ${T::class.simpleName}")
}

@Suppress("NOTHING_TO_INLINE")
inline fun <T: Any> T?.jsExpect(): T {
    return this ?: throw Error("Expected a valid value, got `null` instead!")
}

inline fun <I, reified O> Collection<I>.mapToArray(transform: (I) -> O): Array<O> {
    val iter = iterator()
    return Array(size) { transform(iter.next()) }
}
