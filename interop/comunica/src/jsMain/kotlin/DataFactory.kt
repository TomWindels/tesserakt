@file:JsNonModule
@file:JsModule("rdf-data-factory")

// comunica has its own data factory type, so it can model variables, and is required directly in the
//  binding factory
@JsName("DataFactory")
external class DataFactoryJs {

    /*
    The methods don't actually return 'any', but specced types. However, the types themselves are not important for
    our limited use
     */

    @OptIn(ExperimentalWasmJsInterop::class)
    fun variable(name: String): JsAny

    @OptIn(ExperimentalWasmJsInterop::class)
    fun namedNode(value: String): JsAny

    @OptIn(ExperimentalWasmJsInterop::class)
    fun blankNode(blankNode: String): JsAny

    @OptIn(ExperimentalWasmJsInterop::class)
    fun literal(value: String, languageOrDatatype: JsAny? /* named term (type) OR string (language) */): JsAny

    @OptIn(ExperimentalWasmJsInterop::class)
    fun defaultGraph(): JsAny

}
