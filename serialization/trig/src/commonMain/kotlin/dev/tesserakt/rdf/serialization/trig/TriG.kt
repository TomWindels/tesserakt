package dev.tesserakt.rdf.serialization.trig

import dev.tesserakt.rdf.serialization.common.Format
import dev.tesserakt.rdf.serialization.common.Serializer

object TriG: Format<TriGConfig>() {

    override fun default(): Serializer {
        return TriGSerializer
    }

    override fun build(config: TriGConfig.() -> Unit): Serializer {
        val conf = TriGConfig()
        conf.apply(config)
        return TriGSerializer(conf)
    }

}
