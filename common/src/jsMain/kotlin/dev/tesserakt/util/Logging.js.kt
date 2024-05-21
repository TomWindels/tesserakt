package dev.tesserakt.util

actual fun printerrln(message: String) {
    console.log("\u001b[31m$message\u001b[0m")
}
