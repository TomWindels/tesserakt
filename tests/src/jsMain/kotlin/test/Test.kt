package test

fun interface Test {
    suspend fun test(): Result<Unit>
}
