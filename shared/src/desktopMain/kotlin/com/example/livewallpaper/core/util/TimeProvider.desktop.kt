package com.example.livewallpaper.core.util

actual object TimeProvider {
    actual fun currentTimeMillis(): Long {
        return System.currentTimeMillis()
    }
}
