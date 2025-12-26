package com.example.livewallpaper.core.util

/**
 * 时间提供者接口
 * 用于获取当前时间戳，支持跨平台
 */
expect object TimeProvider {
    /**
     * 获取当前时间戳（毫秒）
     * @return 当前时间的毫秒数
     */
    fun currentTimeMillis(): Long
}
