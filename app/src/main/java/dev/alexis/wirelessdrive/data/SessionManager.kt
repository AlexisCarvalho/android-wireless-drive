package dev.alexis.wirelessdrive.data

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Single source of truth for "the current session just became invalid",
 * shared by every ViewModel that talks to the API.
 *
 * Previously this lived only inside GalleryViewModel: its own SharedFlow,
 * plus a collector local to GalleryScreen that called onLogoutClick. Any
 * other screen (e.g. the media viewer) had no way to react to a 401/403 at
 * all -- it would just show a generic error and leave a dead token behind.
 *
 * Now there's a single instance (see AppContainer) and a single collector,
 * set up once in WirelessDriveApp, so a session expiring anywhere in the
 * app clears the token and lands the user back on login regardless of
 * which screen they were on.
 */
class SessionManager(private val tokenManager: TokenManager) {

    private val _sessionExpired = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val sessionExpired: SharedFlow<Unit> = _sessionExpired.asSharedFlow()

    /**
     * Call with the HTTP status code of a failed API response. If it's an
     * auth failure, clears the token and emits the session-expired event,
     * returning true so the caller knows to stop what it was doing --
     * the user is about to be booted to the login screen, so there's no
     * point also showing a generic error message.
     */
    suspend fun handleAuthFailure(responseCode: Int): Boolean {
        if (responseCode != 401 && responseCode != 403) return false

        tokenManager.clearToken()
        _sessionExpired.emit(Unit)
        return true
    }

    /** Explicit, user-initiated logout -- goes through the same signal. */
    suspend fun logout() {
        tokenManager.clearToken()
        _sessionExpired.emit(Unit)
    }
}