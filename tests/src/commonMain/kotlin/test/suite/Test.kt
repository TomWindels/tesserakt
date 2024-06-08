package test.suite

import kotlin.jvm.JvmInline

fun interface Test {

    suspend fun test(): Result

    interface Result {

        fun isSuccess(): Boolean

        fun exceptionOrNull(): Throwable?

        @JvmInline
        value class Failure(private val cause: Throwable): Result {
            override fun exceptionOrNull() = cause
            override fun isSuccess() = false
        }

        companion object {

            fun <R: Result> kotlin.Result<R>.unbox(): Result = fold(
                onSuccess = { it },
                onFailure = { Failure(it) }
            )

        }

    }

}
