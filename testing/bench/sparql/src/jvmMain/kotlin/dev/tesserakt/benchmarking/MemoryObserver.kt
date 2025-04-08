package dev.tesserakt.benchmarking

import java.io.BufferedWriter
import java.io.FileWriter

class MemoryObserver(filepath: String): Runnable {

    private val writer = BufferedWriter(FileWriter(filepath))
    private lateinit var memoryThread: Thread
    private val runtime = Runtime.getRuntime()
    private var running = false

    fun start() {
        writer.write("epoch (ms),free memory,total memory\n")
        running = true
        memoryThread = Thread(this)
            .apply { start() }
    }

    fun stop() {
        running = false
        memoryThread.join()
        writer.close()
    }

    fun reset() {
        runtime.gc()
    }

    override fun run() {
        while (running) {
            val free = runtime.freeMemory()
            val total = runtime.totalMemory()
            writer.write("${currentEpochMs()},$free,$total\n")
            Thread.sleep(2)
        }
    }

}
