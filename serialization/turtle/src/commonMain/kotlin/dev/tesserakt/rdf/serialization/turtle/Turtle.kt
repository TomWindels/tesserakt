package dev.tesserakt.rdf.serialization.turtle

import dev.tesserakt.rdf.serialization.common.Format
import dev.tesserakt.rdf.serialization.common.Serializer

object Turtle: Format<TurtleConfig>() {

    override fun default(): Serializer {
        return TurtleSerializer
    }

    override fun build(config: TurtleConfig.() -> Unit): Serializer {
        val conf = TurtleConfig()
        conf.apply(config)
        return TurtleSerializer(conf)
    }

}
