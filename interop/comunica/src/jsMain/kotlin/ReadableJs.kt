@file:JsNonModule
@file:JsModule("node:stream")

@OptIn(ExperimentalWasmJsInterop::class)
@JsName("Readable")
abstract external class ReadableJs(opts: dynamic) {

    @JsName("_read")
    abstract fun read()

    protected fun push(item: JsAny?)

}
