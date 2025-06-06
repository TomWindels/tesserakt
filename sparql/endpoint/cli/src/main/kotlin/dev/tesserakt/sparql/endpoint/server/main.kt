package dev.tesserakt.sparql.endpoint.server

import com.github.ajalt.clikt.core.main

fun main(args: Array<String>) =
    CliEntryPoint(
        run = { config -> Server(config).run() }
    ).main(args)
