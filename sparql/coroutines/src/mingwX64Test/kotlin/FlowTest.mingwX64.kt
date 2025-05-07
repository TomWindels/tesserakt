import kotlinx.coroutines.*

actual class DispatchHelper(
    private val _dispatcher: CloseableCoroutineDispatcher
) {
    actual val dispatcher: CoroutineDispatcher get() = _dispatcher
    actual fun close() {
        _dispatcher.close()
    }
}

@OptIn(ExperimentalCoroutinesApi::class, DelicateCoroutinesApi::class)
actual fun createContext(name: String): DispatchHelper {
    return DispatchHelper(newSingleThreadContext(name = name))
}
