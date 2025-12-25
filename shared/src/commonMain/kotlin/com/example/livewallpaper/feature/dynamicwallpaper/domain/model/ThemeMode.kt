package com.example.livewallpaper.feature.dynamicwallpaper.domain.model

import kotlinx.serialization.Serializable

/**
 * 主题模式
 */
@Serializable
enum class ThemeMode {
    /** 跟随系统 */
    SYSTEM,
    /** 浅色模式 */
    LIGHT,
    /** 深色模式 */
    DARK
}
