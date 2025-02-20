package dev.tesserakt.rdf.n3

@RequiresOptIn("N3 support is limited and not spec compliant. API changes may occur if the implementation becomes spec compliant.")
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
annotation class ExperimentalN3Api
