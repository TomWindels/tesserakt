package dev.tesserakt.concurrent

actual sealed class ConcurrencyMode actual constructor() {

    internal actual abstract fun toTaskRunner(): TaskRunner

    actual companion object

}
