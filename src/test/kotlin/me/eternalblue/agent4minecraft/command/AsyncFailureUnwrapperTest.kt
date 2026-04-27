package me.eternalblue.agent4minecraft.command

import me.eternalblue.agent4minecraft.backend.BackendTimeoutException
import java.util.concurrent.CompletionException
import java.util.concurrent.ExecutionException
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertSame

class AsyncFailureUnwrapperTest {
    @Test
    fun `preserves mapped backend exception instead of exposing nested transport cause`() {
        val nestedCause = IllegalStateException("raw grpc detail")
        val mapped = BackendTimeoutException("ask request timed out", nestedCause)
        val wrapped = CompletionException(mapped)

        val result = AsyncFailureUnwrapper.unwrap(wrapped)

        assertSame(mapped, result)
    }

    @Test
    fun `unwraps async wrappers but stops at first real application exception`() {
        val mapped = BackendTimeoutException("sync timed out")
        val wrapped = CompletionException(ExecutionException(mapped))

        val result = AsyncFailureUnwrapper.unwrap(wrapped)

        assertIs<BackendTimeoutException>(result)
        assertSame(mapped, result)
    }
}
