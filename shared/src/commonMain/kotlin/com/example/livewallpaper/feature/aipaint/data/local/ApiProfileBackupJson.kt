package com.example.livewallpaper.feature.aipaint.data.local

import com.example.livewallpaper.feature.aipaint.domain.model.ApiProfileBackup
import com.example.livewallpaper.feature.aipaint.domain.model.ApiProfileImportError
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** 绘画 API 配置备份 JSON 的解码结果。 */
internal sealed interface ApiProfileBackupDecodeResult {
    /** @property backup 已完成结构和字段校验的备份。 */
    data class Success(val backup: ApiProfileBackup) : ApiProfileBackupDecodeResult

    /** @property error 解码或校验失败原因。 */
    data class Failure(val error: ApiProfileImportError) : ApiProfileBackupDecodeResult
}

/** 负责绘画 API 配置备份 JSON 的跨平台编码、解码和输入校验。 */
internal object ApiProfileBackupJson {
    private const val MAX_JSON_LENGTH = 1024 * 1024

    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    /**
     * 将备份编码为易读的 JSON。
     *
     * @param backup 要导出的配置备份。
     * @return 可写入文件的 JSON 字符串。
     */
    fun encode(backup: ApiProfileBackup): String = json.encodeToString(backup)

    /**
     * 解码并完整校验备份；失败时不返回任何部分数据。
     *
     * @param content 从用户选择的 JSON 文件读取的内容。
     * @return 校验后的备份或稳定错误类型。
     */
    fun decode(content: String): ApiProfileBackupDecodeResult {
        if (content.length > MAX_JSON_LENGTH) {
            return ApiProfileBackupDecodeResult.Failure(ApiProfileImportError.FILE_TOO_LARGE)
        }
        val backup = try {
            json.decodeFromString<ApiProfileBackup>(content)
        } catch (_: SerializationException) {
            return ApiProfileBackupDecodeResult.Failure(ApiProfileImportError.INVALID_JSON)
        } catch (_: IllegalArgumentException) {
            return ApiProfileBackupDecodeResult.Failure(ApiProfileImportError.INVALID_JSON)
        }
        if (backup.formatVersion != ApiProfileBackup.CURRENT_FORMAT_VERSION) {
            return ApiProfileBackupDecodeResult.Failure(ApiProfileImportError.UNSUPPORTED_VERSION)
        }
        val normalizedBackup = backup.copy(
            activeProfileId = backup.activeProfileId?.trim(),
            profiles = backup.profiles.map { profile ->
                profile.copy(
                    id = profile.id.trim(),
                    name = profile.name.trim(),
                    baseUrl = profile.baseUrl.trim().trimEnd('/'),
                )
            },
        )
        if (normalizedBackup.profiles.any { profile ->
                profile.id.isBlank() ||
                    profile.name.isBlank() ||
                    profile.baseUrl.isBlank() ||
                    profile.token.isBlank()
            }
        ) {
            return ApiProfileBackupDecodeResult.Failure(ApiProfileImportError.INVALID_PROFILE)
        }
        val profileIds = normalizedBackup.profiles.map { it.id }
        if (profileIds.distinct().size != profileIds.size) {
            return ApiProfileBackupDecodeResult.Failure(ApiProfileImportError.DUPLICATE_PROFILE_ID)
        }
        if (normalizedBackup.activeProfileId != null && normalizedBackup.activeProfileId !in profileIds) {
            return ApiProfileBackupDecodeResult.Failure(ApiProfileImportError.INVALID_ACTIVE_PROFILE)
        }
        return ApiProfileBackupDecodeResult.Success(normalizedBackup)
    }
}
