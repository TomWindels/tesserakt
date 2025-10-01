package dev.tesserakt.benchmarking.endpoint

import io.ktor.client.engine.*
import io.ktor.client.engine.java.*

internal actual val engine: HttpClientEngineFactory<*>
    get() = Java
