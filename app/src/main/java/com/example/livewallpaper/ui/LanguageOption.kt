package com.example.livewallpaper.ui

import androidx.annotation.StringRes
import com.example.livewallpaper.R
import java.util.Locale

/**
 * 语言选项枚举
 */
enum class LanguageOption(
    val localeTag: String,
    @StringRes val labelRes: Int
) {
    ENGLISH("en", R.string.language_en),
    CHINESE("zh-CN", R.string.language_zh);

    companion object {
        /**
         * 根据语言标签获取对应的语言选项
         * @param tag 语言标签，如 "en", "zh-CN"
         * @return 对应的语言选项，如果找不到则根据系统语言返回默认值
         */
        fun fromTag(tag: String?): LanguageOption {
            if (tag == null) {
                // 根据系统语言返回默认值
                val systemLocale = Locale.getDefault()
                return when {
                    systemLocale.language == "zh" -> CHINESE
                    else -> ENGLISH
                }
            }
            return entries.find { it.localeTag == tag } ?: ENGLISH
        }
    }
}
