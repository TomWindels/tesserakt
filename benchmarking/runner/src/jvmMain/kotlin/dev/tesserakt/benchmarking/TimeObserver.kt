package dev.tesserakt.benchmarking

import java.io.BufferedWriter
import java.io.FileWriter

class TimeObserver(filepath: String) {

    private val writer = BufferedWriter(FileWriter(filepath))
    private lateinit var id: String
    private var start = 0L

    init {
        writer.write("name,start epoch (ms),end epoch (ms)\n")
    }

    fun start(id: String) {
        this.id = id
        start = currentEpochMs()
    }

    fun end(id: String) {
        val end = currentEpochMs()
        require(this.id == id)
        writer.write("$id,$start,$end\n")
    }

    fun stop() {
        writer.close()
    }

}
