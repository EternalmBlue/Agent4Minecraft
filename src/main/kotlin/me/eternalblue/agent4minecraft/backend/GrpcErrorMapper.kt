package me.eternalblue.agent4minecraft.backend

import io.grpc.Status
import io.grpc.StatusException
import io.grpc.StatusRuntimeException
import me.eternalblue.agent4minecraft.i18n.PluginMessages
import java.util.concurrent.ExecutionException

object GrpcErrorMapper {
    fun map(
        throwable: Throwable,
        actionLabel: String,
        messages: PluginMessages,
    ): BackendClientException {
        val rootCause = unwrap(throwable)
        val status = extractStatus(rootCause)
            ?: return BackendTransportException(
                messages.backendTransportFailure(
                    action = actionLabel,
                    detail = rootCause.message ?: messages.unknownError(),
                ),
                rootCause,
            )

        return when (status.code) {
            Status.Code.UNAUTHENTICATED ->
                BackendAuthenticationException(
                    messages.backendAuthenticationFailed(),
                    rootCause,
                )

            Status.Code.PERMISSION_DENIED ->
                BackendPermissionException(
                    messages.backendPermissionDenied(actionLabel),
                    rootCause,
                )

            Status.Code.DEADLINE_EXCEEDED ->
                BackendTimeoutException(
                    messages.backendTimedOut(actionLabel),
                    rootCause,
                )

            Status.Code.UNAVAILABLE ->
                BackendUnavailableException(
                    messages.backendUnavailable(),
                    rootCause,
                )

            Status.Code.INVALID_ARGUMENT,
            Status.Code.NOT_FOUND,
            Status.Code.ALREADY_EXISTS,
            Status.Code.FAILED_PRECONDITION ->
                BackendRequestException(
                    messages.backendRequestRejected(
                        action = actionLabel,
                        detail = status.description ?: messages.invalidRequest(),
                    ),
                    rootCause,
                )

            else ->
                BackendTransportException(
                    messages.backendTransportFailure(
                        action = actionLabel,
                        detail = status.description ?: status.code.name,
                    ),
                    rootCause,
                )
        }
    }

    private fun unwrap(throwable: Throwable): Throwable {
        return when (throwable) {
            is ExecutionException -> throwable.cause?.let(::unwrap) ?: throwable
            else -> throwable
        }
    }

    private fun extractStatus(throwable: Throwable): Status? {
        return when (throwable) {
            is StatusException -> throwable.status
            is StatusRuntimeException -> throwable.status
            else -> null
        }
    }
}
