package dev.tesserakt.util

import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

/**
 * A simple Read Write locking mechanism, responsible for preventing race conditions & concurrent modifications. Should
 *  NOT be used to guard suspending code!
 */
expect class RWLock()

expect inline fun lockForWriting(lock: RWLock)

expect inline fun lockForReading(lock: RWLock)

expect inline fun unlockForWriting(lock: RWLock)

expect inline fun unlockForReading(lock: RWLock)

@OptIn(ExperimentalContracts::class)
inline fun <T> RWLock.withWriteLock(block: () -> T): T {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }
    lockForWriting(this)
    try {
        return block()
    } finally {
        unlockForWriting(this)
    }
}

@OptIn(ExperimentalContracts::class)
inline fun <T> RWLock.withReadLock(block: () -> T): T {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }
    lockForReading(this)
    try {
        return block()
    } finally {
        unlockForReading(this)
    }
}
