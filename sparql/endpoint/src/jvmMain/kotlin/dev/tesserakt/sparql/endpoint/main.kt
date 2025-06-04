package dev.tesserakt.sparql.endpoint

import com.github.ajalt.clikt.core.main

fun main(args: Array<String>) =
    CliEntryPoint(
        run = { config -> Endpoint(config).run() }
    ).main(args)
