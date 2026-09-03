package dev.tesserakt

actual inline fun withMultithreading(block: () -> Unit) {
    // no multithreading available here
    block()
}
