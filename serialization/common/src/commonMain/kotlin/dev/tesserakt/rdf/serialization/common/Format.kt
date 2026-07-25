package dev.tesserakt.rdf.serialization.common

abstract class Format<Config> {

    protected abstract fun default(): Serializer

    protected abstract fun build(config: Config.() -> Unit): Serializer

    companion object {

        fun <F: Format<*>> serializer(format: F): Serializer {
            return format.default()
        }

        fun <C, F: Format<C>> serializer(format: F, config: C.() -> Unit): Serializer {
            return format.build(config)
        }

    }
}
