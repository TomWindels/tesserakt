package dev.tesserakt.sparql.benchmark.replay

import dev.tesserakt.rdf.types.Store

class VersionedStore {

    class Builder {

        private val versions = mutableListOf<Store>()

        fun appendVersion(store: Store) {
            versions.add(store)
        }

    }

}
