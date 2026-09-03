package dev.tesserakt.concurrent

import android.os.Build

actual inline fun spinLoopHint() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Thread.onSpinWait()
    }
    // we don't emit anything otherwise, but it's generally not recommended to spin loop on android anyway
}
