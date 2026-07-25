@file:JsNonModule
@file:JsModule("@comunica/utils-bindings-factory")

@JsName("BindingsFactory")
external class BindingsFactoryJs(factory: DataFactoryJs) {

    @OptIn(ExperimentalWasmJsInterop::class)
    fun bindings(
        /** [ [ VARIABLE, TERM ], ... ] **/
        arr: JsArray<JsArray<JsAny>>
    ): BindingsJs

}
