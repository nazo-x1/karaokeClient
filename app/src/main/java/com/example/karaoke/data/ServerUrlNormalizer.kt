package com.example.karaoke.data

import java.net.URI

object ServerUrlNormalizer {
    /**
     * 规范为「服务器根地址」：补全 http://、去掉路径与末尾斜杠。
     * 例：`192.168.1.20:15233/song` → `http://192.168.1.20:15233`
     */
    fun normalize(input: String): String? {
        var raw = input.trim()
        if (raw.isBlank()) return null

        if (!raw.contains("://")) {
            raw = "http://$raw"
        }

        return try {
            val uri = URI(raw)
            val scheme = uri.scheme?.lowercase() ?: "http"
            val host = uri.host ?: uri.authority?.substringBefore(':') ?: return null
            val port = uri.port
            val defaultPort = when (scheme) {
                "https" -> 443
                else -> 80
            }
            if (port > 0 && port != defaultPort) {
                "$scheme://$host:$port"
            } else {
                "$scheme://$host"
            }
        } catch (_: Exception) {
            raw.trimEnd('/').takeIf { it.isNotBlank() }
        }
    }
}
