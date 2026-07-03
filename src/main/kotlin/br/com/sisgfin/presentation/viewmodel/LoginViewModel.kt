package br.com.sisgfin.presentation.viewmodel

import br.com.sisgfin.AuthService
import br.com.sisgfin.User
import br.com.sisgfin.presentation.state.LoginUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class LoginViewModel(private val authService: AuthService) : BaseViewModel() {
    private val _uiState = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    val uiState = _uiState.asStateFlow()

    fun login(username: String, passwordRaw: String, onLoginSuccess: (User) -> Unit) {
        viewModelScope.launch {
            _uiState.value = LoginUiState.Loading
            try {
                val user = authService.authenticate(username, passwordRaw)
                if (user != null) {
                    _uiState.value = LoginUiState.Success
                    onLoginSuccess(user)
                } else {
                    _uiState.value = LoginUiState.Error("Usuário ou senha inválidos ou conta desativada.")
                }
            } catch (e: Exception) {
                _uiState.value = LoginUiState.Error(e.message ?: "Erro ao autenticar")
            }
        }
    }

    fun resetState() {
        _uiState.value = LoginUiState.Idle
    }
}
