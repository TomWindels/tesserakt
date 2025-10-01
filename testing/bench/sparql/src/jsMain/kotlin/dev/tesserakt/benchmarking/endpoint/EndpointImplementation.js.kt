package dev.tesserakt.benchmarking.endpoint

import io.ktor.client.engine.*
import io.ktor.client.engine.js.*

internal actual val engine: HttpClientEngineFactory<*>
    get() = Js
