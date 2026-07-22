package dev.alexis.mediagallery.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import dev.alexis.mediagallery.data.AuthResponse
import dev.alexis.mediagallery.data.LoginRequest
import dev.alexis.mediagallery.data.RegisterRequest
import dev.alexis.mediagallery.data.SavedProfile
import dev.alexis.mediagallery.data.SavedProfileManager
import dev.alexis.mediagallery.data.TokenManager
import dev.alexis.mediagallery.network.ApiService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LoginUiState(
    val name: String = "",
    val code: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val savedProfiles: List<SavedProfile> = emptyList(),
    val isRegisterMode: Boolean = false
)

class LoginViewModel(
    private val apiService: ApiService,
    private val tokenManager: TokenManager,
    private val savedProfileManager: SavedProfileManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    init {
        loadSavedProfiles()
    }

    fun onNameChange(value: String) {
        _uiState.update { it.copy(name = value, errorMessage = null) }
    }

    fun onCodeChange(value: String) {
        _uiState.update { it.copy(code = value, errorMessage = null) }
    }

    fun onPasswordChange(value: String) {
        _uiState.update { it.copy(password = value, errorMessage = null) }
    }

    fun toggleMode() {
        _uiState.update { it.copy(isRegisterMode = !it.isRegisterMode, errorMessage = null) }
    }

    fun loadSavedProfiles() {
        viewModelScope.launch {
            val profiles = savedProfileManager.loadProfiles()
            _uiState.update { it.copy(savedProfiles = profiles) }
        }
    }

    fun selectProfile(profile: SavedProfile) {
        _uiState.update {
            it.copy(
                code = profile.code,
                password = profile.password,
                errorMessage = null
            )
        }
    }

    fun removeProfile(profile: SavedProfile) {
        viewModelScope.launch {
            savedProfileManager.removeProfile(profile.code)
            val updatedProfiles = _uiState.value.savedProfiles.filterNot { it.code == profile.code }
            _uiState.update { it.copy(savedProfiles = updatedProfiles) }
        }
    }

    fun login(onSuccess: () -> Unit) {
        val state = _uiState.value
        val trimmedCode = state.code.trim()
        val trimmedPassword = state.password.trim()
        val trimmedName = state.name.trim()

        if (state.isRegisterMode) {
            if (trimmedName.isBlank() || trimmedCode.isBlank() || trimmedPassword.isBlank()) {
                _uiState.update { it.copy(errorMessage = "Preencha nome, código e senha") }
                return
            }
        } else if (trimmedCode.isBlank() || trimmedPassword.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Preencha código e senha") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val response = if (state.isRegisterMode) {
                    apiService.register(
                        RegisterRequest(trimmedName, trimmedCode, trimmedPassword)
                    )
                } else {
                    apiService.login(LoginRequest(trimmedCode, trimmedPassword))
                }

                if (response.isSuccessful) {
                    val token = response.body()?.token?.takeIf { it.isNotBlank() }

                    if (!token.isNullOrBlank()) {
                        tokenManager.saveToken(token)
                        savedProfileManager.saveProfile(trimmedCode, trimmedPassword)
                        _uiState.update { it.copy(isLoading = false) }
                        onSuccess()
                        return@launch
                    }

                    if (state.isRegisterMode) {
                        val loginResponse =
                            apiService.login(LoginRequest(trimmedCode, trimmedPassword))
                        val loginToken = loginResponse.body()?.token?.takeIf { it.isNotBlank() }

                        if (loginResponse.isSuccessful && !loginToken.isNullOrBlank()) {
                            tokenManager.saveToken(loginToken)
                            savedProfileManager.saveProfile(trimmedCode, trimmedPassword)
                            _uiState.update { it.copy(isLoading = false) }
                            onSuccess()
                            return@launch
                        }

                        val loginMessage = parseErrorMessage(loginResponse.errorBody()?.string())
                            ?: "Cadastro realizado, mas não foi possível entrar automaticamente"
                        _uiState.update { it.copy(isLoading = false, errorMessage = loginMessage) }
                        return@launch
                    }
                }

                val message = parseErrorMessage(response.errorBody()?.string())
                    ?: if (state.isRegisterMode) {
                        "Não foi possível concluir o cadastro"
                    } else {
                        "Código ou senha incorretos"
                    }
                _uiState.update { it.copy(isLoading = false, errorMessage = message) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = "Erro ao conectar: ${e.message}")
                }
            }
        }
    }

    private fun parseErrorMessage(rawErrorBody: String?): String? {
        if (rawErrorBody.isNullOrBlank()) return null
        return try {
            Gson().fromJson(rawErrorBody, AuthResponse::class.java)?.error
        } catch (e: Exception) {
            null
        }
    }
}
