package dev.tesserakt.concurrent

actual inline fun spinLoopHint() {
    Thread.onSpinWait()
}
