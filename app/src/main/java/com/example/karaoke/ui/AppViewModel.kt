package com.example.karaoke.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.karaoke.data.KaraokeRepository
import com.example.karaoke.data.ServerUrlNormalizer
import com.example.karaoke.ui.navigation.AppPhase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AppViewModel(
    private val repository: KaraokeRepository,
) : ViewModel() {

    private val _phase = MutableStateFlow(
        if (repository.serverUrl.isNotBlank()) AppPhase.Player else AppPhase.Setup,
    )
    val phase: StateFlow<AppPhase> = _phase.asStateFlow()

    private val _setupError = MutableStateFlow<String?>(null)
    val setupError: StateFlow<String?> = _setupError.asStateFlow()

    private val _connecting = MutableStateFlow(false)
    val connecting: StateFlow<Boolean> = _connecting.asStateFlow()

    fun connect(serverUrl: String) {
        val normalized = ServerUrlNormalizer.normalize(serverUrl)
        if (normalized.isNullOrBlank()) {
            _setupError.value = "请输入服务器地址，示例：http://192.168.1.20:15233"
            return
        }
        viewModelScope.launch {
            _connecting.value = true
            _setupError.value = null
            val result = repository.probe(normalized)
            _connecting.value = false
            result.fold(
                onSuccess = {
                    repository.saveServer(normalized)
                    _phase.value = AppPhase.Player
                },
                onFailure = { _setupError.value = it.message ?: "连接失败" },
            )
        }
    }
}
