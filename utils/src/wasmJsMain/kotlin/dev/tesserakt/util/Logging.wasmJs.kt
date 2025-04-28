package dev.tesserakt.util

actual fun printerrln(message: String) {
    js("{ console.error(message); }")
}
