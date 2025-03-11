package dev.tesserakt.sparql

import dev.tesserakt.sparql.ast.TriplePattern


private var generatedBindingIndex = 0

fun newAnonymousBinding() = TriplePattern.GeneratedBinding(id = generatedBindingIndex++)
