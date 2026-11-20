package com.example.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.OperationType
import com.example.core.friendlyFirestoreMessage
import com.example.core.handleFirestoreError
import com.example.data.model.UserAccount
import com.example.data.model.UserRole
import com.example.data.repository.InstituteDirectoryRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

data class AuthUiState(
    val loading: Boolean = true,
    val firebaseUser: FirebaseUser? = null,
    val userAccount: UserAccount? = null,
    val selectedPortalTab: UserRole = UserRole.STUDENT,
    // Admin can optionally preview Teacher/Student dashboards; non-admins are strictly locked to userAccount.role
    val activeWorkspaceRole: UserRole = UserRole.STUDENT,
    val isBootstrappedAdmin: Boolean = false,
    val claimingCode: Boolean = false,
    val errorMessage: String? = null,
    val statusMessage: String? = null
)

fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        trySend(firebaseAuth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

class AuthViewModel(
    private val directoryRepository: InstituteDirectoryRepository,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState(loading = true, firebaseUser = auth.currentUser))
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    private var accountJob: kotlinx.coroutines.Job? = null

    init {
        viewModelScope.launch {
            auth.authStateFlow().collect { user ->
                accountJob?.cancel()
                if (user == null) {
                    _uiState.update {
                        it.copy(
                            loading = false,
                            firebaseUser = null,
                            userAccount = null,
                            isBootstrappedAdmin = false,
                            activeWorkspaceRole = it.selectedPortalTab
                        )
                    }
                } else {
                    hydrateAuthenticatedUser(user)
                }
            }
        }
    }

    private fun hydrateAuthenticatedUser(user: FirebaseUser) {
        val isSuper = directoryRepository.isBootstrappedAdmin(user)
        _uiState.update { it.copy(loading = true, firebaseUser = user, isBootstrappedAdmin = isSuper, errorMessage = null) }
        accountJob = viewModelScope.launch {
            val ensureResult = directoryRepository.ensureUserAccount(user)
            if (ensureResult.isFailure) {
                val err = ensureResult.exceptionOrNull()
                _uiState.update {
                    it.copy(
                        loading = false,
                        errorMessage = err?.let { e -> friendlyFirestoreMessage(e, "Unable to load institute profile.") }
                    )
                }
                return@launch
            }
            directoryRepository.observeUserAccount(user.uid)
                .catch { error ->
                    val fsErr = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (fsErr == null && error is CancellationException) throw error
                    handleFirestoreError(fsErr ?: error, OperationType.GET, "users/${user.uid}")
                    _uiState.update {
                        it.copy(
                            loading = false,
                            errorMessage = friendlyFirestoreMessage(fsErr ?: error, "Unable to observe account.")
                        )
                    }
                }
                .collect { account ->
                    val resolvedRole = when {
                        isSuper -> UserRole.ADMIN
                        account != null -> account.roleEnum()
                        else -> UserRole.STUDENT
                    }
                    _uiState.update { prev ->
                        val nextWorkspace = if (resolvedRole == UserRole.ADMIN) {
                            // Keep admin's selected workspace if already loaded, else default to ADMIN
                            if (prev.userAccount != null) prev.activeWorkspaceRole else UserRole.ADMIN
                        } else {
                            resolvedRole
                        }
                        prev.copy(
                            loading = false,
                            firebaseUser = user,
                            userAccount = account,
                            isBootstrappedAdmin = isSuper || resolvedRole == UserRole.ADMIN,
                            activeWorkspaceRole = nextWorkspace
                        )
                    }
                }
        }
    }

    fun selectPortalTab(role: UserRole) {
        _uiState.update { it.copy(selectedPortalTab = role, errorMessage = null) }
    }

    fun switchAdminPreviewRole(role: UserRole) {
        val current = _uiState.value
        val canSwitch = current.isBootstrappedAdmin || current.userAccount?.roleEnum() == UserRole.ADMIN
        if (canSwitch) {
            _uiState.update { it.copy(activeWorkspaceRole = role, statusMessage = "Switched workspace to ${role.name.lowercase().replaceFirstChar(Char::uppercase)} Portal") }
        }
    }

    fun signInWithGoogleToken(idToken: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, errorMessage = null) }
            try {
                val credential = GoogleAuthProvider.getCredential(idToken, null)
                auth.signInWithCredential(credential).await()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Google Firebase sign-in failed", e)
                _uiState.update {
                    it.copy(
                        loading = false,
                        errorMessage = e.localizedMessage ?: "Google Sign-In failed. Please try again."
                    )
                }
            }
        }
    }

    fun claimPortalCode(code: String, passcode: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(claimingCode = true, errorMessage = null, statusMessage = null) }
            val result = directoryRepository.claimInstitutePortalCode(code, passcode)
            result.fold(
                onSuccess = { updated ->
                    _uiState.update {
                        it.copy(
                            claimingCode = false,
                            userAccount = updated,
                            activeWorkspaceRole = updated.roleEnum(),
                            statusMessage = "Linked successfully to ${updated.instituteCode} (${updated.role})"
                        )
                    }
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            claimingCode = false,
                            errorMessage = err.localizedMessage ?: "Failed to verify Institute ID & Passcode."
                        )
                    }
                }
            )
        }
    }

    fun reportAuthError(message: String) {
        _uiState.update { it.copy(loading = false, errorMessage = message) }
    }

    fun clearMessages() {
        _uiState.update { it.copy(errorMessage = null, statusMessage = null) }
    }

    fun signOut() {
        viewModelScope.launch {
            withContext(NonCancellable) {
                accountJob?.cancel()
                accountJob = null
                auth.signOut()
            }
        }
    }

    private companion object {
        const val TAG = "AuthVM"
    }
}
