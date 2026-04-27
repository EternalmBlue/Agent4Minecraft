package me.eternalblue.agent4minecraft.backend

sealed class BackendClientException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

class BackendAuthenticationException(
    message: String,
    cause: Throwable? = null,
) : BackendClientException(message, cause)

class BackendTimeoutException(
    message: String,
    cause: Throwable? = null,
) : BackendClientException(message, cause)

class BackendUnavailableException(
    message: String,
    cause: Throwable? = null,
) : BackendClientException(message, cause)

class BackendPermissionException(
    message: String,
    cause: Throwable? = null,
) : BackendClientException(message, cause)

class BackendRequestException(
    message: String,
    cause: Throwable? = null,
) : BackendClientException(message, cause)

class BackendTransportException(
    message: String,
    cause: Throwable? = null,
) : BackendClientException(message, cause)
