package dev.tesserakt.util

import java.util.concurrent.locks.ReentrantReadWriteLock

/**
 * A simple locking mechanism, responsible for preventing race conditions & concurrent modifications. Should
 *  NOT be used to guard suspending code!
 */
actual typealias RWLock = ReentrantReadWriteLock

actual inline fun lockForWriting(lock: RWLock) {
    lock.writeLock().lock()
}

actual inline fun lockForReading(lock: RWLock) {
    lock.readLock().lock()
}

actual inline fun unlockForWriting(lock: RWLock) {
    lock.writeLock().unlock()
}

actual inline fun unlockForReading(lock: RWLock) {
    lock.readLock().unlock()
}
