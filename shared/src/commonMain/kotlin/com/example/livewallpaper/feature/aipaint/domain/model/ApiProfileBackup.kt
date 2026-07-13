package com.example.livewallpaper.feature.aipaint.domain.model

import kotlinx.serialization.Serializable

/**
 * 可跨平台导入、导出的绘画 API 配置备份。
 *
 * @property formatVersion JSON 格式版本，用于后续兼容迁移。
 * @property activeProfileId 导出时启用的配置 ID；为空时导入不会切换当前配置。
 * @property profiles 备份包含的 API 配置，包含访问令牌等敏感信息。
 */
@Serializable
data class ApiProfileBackup(
    val formatVersion: Int = CURRENT_FORMAT_VERSION,
    val activeProfileId: String? = null,
    val profiles: List<ApiProfile>,
) {
    companion object {
        /** 当前支持的备份格式版本。 */
        const val CURRENT_FORMAT_VERSION = 1
    }
}

/** 绘画 API 配置导入失败的稳定错误类型，由平台 UI 映射为本地化文案。 */
enum class ApiProfileImportError {
    FILE_TOO_LARGE,
    INVALID_JSON,
    UNSUPPORTED_VERSION,
    INVALID_PROFILE,
    DUPLICATE_PROFILE_ID,
    INVALID_ACTIVE_PROFILE,
}

/** 绘画 API 配置导入结果。 */
sealed interface ApiProfileImportResult {
    /**
     * 导入成功。
     *
     * @property importedCount 本次备份中的配置数。
     * @property totalCount 合并后保存的配置总数。
     */
    data class Success(
        val importedCount: Int,
        val totalCount: Int,
    ) : ApiProfileImportResult

    /**
     * 导入失败，原有配置不会被修改。
     *
     * @property error 可供平台层本地化展示的错误类型。
     */
    data class Failure(val error: ApiProfileImportError) : ApiProfileImportResult
}
