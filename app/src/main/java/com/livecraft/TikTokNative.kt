package com.livecraft

object TikTokNative {
    init {
        System.loadLibrary("livecraft_tiktok")
    }

    @JvmStatic external fun nativeStart(username: String)
    @JvmStatic external fun nativeStop()

    fun start(username: String) {
        if (username.isNotBlank()) nativeStart(username.trim().removePrefix("@"))
    }
}
