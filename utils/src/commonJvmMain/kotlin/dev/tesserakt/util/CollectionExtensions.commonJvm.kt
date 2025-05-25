package dev.tesserakt.util

actual inline fun IntArray.cloneTo(
    target: IntArray,
    thisOffset: Int,
    targetOffset: Int,
    length: Int
) {
    System.arraycopy(this, thisOffset, target, targetOffset, length)
}
