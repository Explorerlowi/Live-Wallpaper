package com.example.livewallpaper.core.network

import android.util.Log

actual fun platformLog(tag: String, message: String) {
    message.chunked(4000).forEach { chunk ->
        Log.d(tag, chunk)
    }
}
