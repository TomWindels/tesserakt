package dev.tesserakt.sparql

import dev.tesserakt.sparql.types.TriplePattern


private var generatedBindingIndex = 0

fun newAnonymousBinding() = TriplePattern.GeneratedBinding(id = generatedBindingIndex++)
