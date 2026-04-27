package me.eternalblue.agent4minecraft.command

import java.util.concurrent.CompletionException
import java.util.concurrent.ExecutionException

object AsyncFailureUnwrapper {
    fun unwrap(throwable: Throwable?): Throwable? {
        return when (throwable) {
            null -> null
            is CompletionException -> throwable.cause?.let(::unwrap) ?: throwable
            is ExecutionException -> throwable.cause?.let(::unwrap) ?: throwable
            else -> throwable
        }
    }
}
