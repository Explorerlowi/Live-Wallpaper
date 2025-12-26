package com.example.livewallpaper.core.util

/**
 * Android 平台的时间提供者实现
 */
actual object TimeProvider {
    actual fun currentTimeMillis(): Long {
        return System.currentTimeMillis()
    }
}
