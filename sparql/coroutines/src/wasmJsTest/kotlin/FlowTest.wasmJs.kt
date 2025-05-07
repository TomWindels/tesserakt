import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

actual class DispatchHelper {
    actual val dispatcher: CoroutineDispatcher
        get() = Dispatchers.Default
    actual fun close() {
        /* no op */
    }
}

actual fun createContext(name: String): DispatchHelper {
    return DispatchHelper()
}
