package dev.tesserakt.types


interface SizeAwareIterator<T>: Iterator<T> {

    val estimatedSize: Int

}
