package com.example.karaoke.ui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class UiToast(
    val text: String,
    val token: Long,
)

/** 应用内统一消息提示（替代 Toast）。 */
class UiMessenger {
    private val _message = MutableStateFlow<UiToast?>(null)
    val message: StateFlow<UiToast?> = _message.asStateFlow()

    private var token = 0L

    fun show(text: String) {
        val msg = text.trim().ifBlank { "操作成功" }
        _message.value = UiToast(msg, ++token)
    }

    fun dismiss() {
        _message.value = null
    }
}
