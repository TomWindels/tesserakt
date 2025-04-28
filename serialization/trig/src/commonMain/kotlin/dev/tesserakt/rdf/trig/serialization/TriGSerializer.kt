package dev.tesserakt.rdf.trig.serialization

import dev.tesserakt.rdf.serialization.common.ConfigurableSerializer
import dev.tesserakt.rdf.serialization.common.Source
import dev.tesserakt.rdf.serialization.core.open
import dev.tesserakt.rdf.serialization.util.BufferedString
import dev.tesserakt.rdf.types.Quad

object TriGSerializer: ConfigurableSerializer<TRiGConfig>() {

    override val DEFAULT: TRiGConfig
        get() = TRiGConfig()

    override fun serialize(data: Collection<Quad>): Iterator<String> {
        return DEFAULT_FORMATTER.format(TokenEncoder(data))
    }

    override fun serialize(data: Iterator<Quad>): Iterator<String> {
        return DEFAULT_FORMATTER.format(TokenEncoder(data))
    }

    override fun serialize(data: Collection<Quad>, config: TRiGConfig): Iterator<String> {
        return config.formatter.format(TokenEncoder(data))
    }

    override fun serialize(data: Iterator<Quad>, config: TRiGConfig): Iterator<String> {
        return config.formatter.format(TokenEncoder(data))
    }

    override fun deserialize(input: Source): Iterator<Quad> {
        return Deserializer(TokenDecoder(BufferedString(input.open())))
    }

}
