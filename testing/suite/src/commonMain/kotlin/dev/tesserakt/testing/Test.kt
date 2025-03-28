package dev.tesserakt.testing

import kotlin.jvm.JvmInline

fun interface Test {

    suspend fun test(): Result

    interface Result {

        data object Skipped : Result {
            // not considered a failure
            override fun isSuccess(): Boolean = true
            override fun exceptionOrNull(): Throwable? = null
        }

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
