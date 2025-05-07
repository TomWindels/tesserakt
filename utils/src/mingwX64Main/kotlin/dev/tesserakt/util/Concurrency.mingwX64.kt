package dev.tesserakt.util

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.convert
import kotlinx.cinterop.sizeOf
import platform.posix.free
import platform.posix.malloc
import platform.windows.*
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.ref.createCleaner

/**
 * A simple Read Write locking mechanism, responsible for preventing race conditions & concurrent modifications. Should
 *  NOT be used to guard suspending code!
 *
 * Docs of the lock type from Microsoft:
 * https://learn.microsoft.com/en-us/windows/win32/sync/slim-reader-writer--srw--locks
 *
 * This lock is available from Vista and upwards
 */
@OptIn(ExperimentalForeignApi::class, ExperimentalNativeApi::class)
actual class RWLock actual constructor() {
    @Suppress("UNCHECKED_CAST")
    val lock: PSRWLOCK = (malloc(sizeOf<RTL_SRWLOCK>().convert()) as PSRWLOCK).apply {
        InitializeSRWLock(this)
    }
    // making sure the lock is actually freed when the GC clears the instance
    @Suppress("unused")
    private val disposer = createCleaner(lock) { lock -> free(lock) }
}

@OptIn(ExperimentalForeignApi::class)
actual inline fun lockForWriting(lock: RWLock) {
    AcquireSRWLockExclusive(lock.lock)
}

@OptIn(ExperimentalForeignApi::class)
actual inline fun lockForReading(lock: RWLock) {
    AcquireSRWLockShared(lock.lock)
}

@OptIn(ExperimentalForeignApi::class)
actual inline fun unlockForWriting(lock: RWLock) {
    ReleaseSRWLockExclusive(lock.lock)
}

@OptIn(ExperimentalForeignApi::class)
actual inline fun unlockForReading(lock: RWLock) {
    ReleaseSRWLockShared(lock.lock)
}
