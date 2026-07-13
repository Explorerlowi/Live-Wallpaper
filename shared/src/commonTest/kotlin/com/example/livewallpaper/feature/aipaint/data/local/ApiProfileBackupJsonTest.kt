package com.example.livewallpaper.feature.aipaint.data.local

import com.example.livewallpaper.feature.aipaint.domain.model.ApiProfile
import com.example.livewallpaper.feature.aipaint.domain.model.ApiProfileBackup
import com.example.livewallpaper.feature.aipaint.domain.model.ApiProfileImportError
import com.example.livewallpaper.feature.aipaint.domain.model.AuthMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/** 验证绘画 API 配置备份 JSON 的兼容格式和拒绝规则。 */
class ApiProfileBackupJsonTest {
    @Test
    fun encodeAndDecodePreservesProfilesAndActiveProfile() {
        val backup = ApiProfileBackup(
            activeProfileId = "profile-1",
            profiles = listOf(
                ApiProfile(
                    id = "profile-1",
                    name = "Primary",
                    baseUrl = "https://example.com",
                    token = "secret",
                    authMode = AuthMode.OFFICIAL,
                ),
            ),
        )

        val result = assertIs<ApiProfileBackupDecodeResult.Success>(
            ApiProfileBackupJson.decode(ApiProfileBackupJson.encode(backup)),
        )

        assertEquals(backup, result.backup)
    }

    @Test
    fun decodeRejectsDuplicateIdsAfterWhitespaceNormalization() {
        val content = """
            {
              "formatVersion": 1,
              "profiles": [
                {"id":"same","name":"One","baseUrl":"https://one.example","token":"one"},
                {"id":" same ","name":"Two","baseUrl":"https://two.example","token":"two"}
              ]
            }
        """.trimIndent()

        val result = assertIs<ApiProfileBackupDecodeResult.Failure>(ApiProfileBackupJson.decode(content))

        assertEquals(ApiProfileImportError.DUPLICATE_PROFILE_ID, result.error)
    }

    @Test
    fun decodeRejectsActiveProfileMissingFromBackup() {
        val content = """
            {
              "formatVersion": 1,
              "activeProfileId": "missing",
              "profiles": []
            }
        """.trimIndent()

        val result = assertIs<ApiProfileBackupDecodeResult.Failure>(ApiProfileBackupJson.decode(content))

        assertEquals(ApiProfileImportError.INVALID_ACTIVE_PROFILE, result.error)
    }

    @Test
    fun decodeRejectsUnsupportedVersion() {
        val content = """{"formatVersion":2,"profiles":[]}"""

        val result = assertIs<ApiProfileBackupDecodeResult.Failure>(ApiProfileBackupJson.decode(content))

        assertEquals(ApiProfileImportError.UNSUPPORTED_VERSION, result.error)
    }
}
