package dev.tesserakt.stream.ldes

import dev.tesserakt.rdf.types.factory.IndexedStore


fun <StreamElement> MutableVersionedLinkedDataEventStream<StreamElement>.toReadOnlyIndexedStream(): IndexedVersionedLinkedDataEventStream<StreamElement> {
    return IndexedVersionedLinkedDataEventStream(
        identifier = identifier,
        store = IndexedStore(this),
        comparator = comparator,
        transform = transform
    )
}
