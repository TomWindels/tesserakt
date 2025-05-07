package dev.tesserakt.util

/**
 * A simple locking mechanism, responsible for preventing race conditions & concurrent modifications. Should
 *  NOT be used to guard suspending code!
 */
actual class RWLock actual constructor()

actual inline fun lockForReading(lock: RWLock) {
    /* no-op */
}

actual inline fun lockForWriting(lock: RWLock) {
    /* no-op */
}

actual inline fun unlockForReading(lock: RWLock) {
    /* no-op */
}

actual inline fun unlockForWriting(lock: RWLock) {
    /* no-op */
}
