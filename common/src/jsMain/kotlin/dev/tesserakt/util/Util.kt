package dev.tesserakt.util


inline fun <reified T> Any.jsCastOrBail(): T {
    return this as? T ?: throw Error("Invalid type: ${this::class.simpleName}\nExpected ${T::class.simpleName}")
}

@Suppress("NOTHING_TO_INLINE")
inline fun <T: Any> T?.jsExpect(): T {
    return this ?: throw Error("Expected a valid value, got `null` instead!")
}
