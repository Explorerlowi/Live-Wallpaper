package com.example.livewallpaper.desktop

import com.example.livewallpaper.di.appModule
import com.example.livewallpaper.di.platformModule
import com.example.livewallpaper.feature.aipaint.domain.repository.PaintDataArchiveGateway
import com.example.livewallpaper.feature.aipaint.domain.repository.PaintDraftRepository
import com.example.livewallpaper.feature.aipaint.domain.usecase.ExportPaintDataUseCase
import com.example.livewallpaper.feature.aipaint.domain.usecase.ImportPaintDataUseCase
import com.example.livewallpaper.feature.aipaint.domain.usecase.PreviewPaintDataImportUseCase
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin

/** Smoke tests desktop backup localization and the runtime dependency graph used by settings. */
class DesktopPaintBackupIntegrationTest {
    @AfterTest
    fun tearDownKoin() {
        if (GlobalContext.getOrNull() != null) stopKoin()
    }

    @Test
    fun backupStringsLoadForEnglishAndChinese() {
        assertEquals("Export painting data", desktopStringsFor("en").paintDataExport)
        assertEquals("导出绘画数据", desktopStringsFor("zh").paintDataExport)
        assertEquals("Select a ZIP backup", desktopStringsFor("en").paintDataImportSelectFile)
        assertEquals("选择 ZIP 备份", desktopStringsFor("zh").paintDataImportSelectFile)
    }

    @Test
    fun desktopModulesProvideBackupDependencies() {
        if (GlobalContext.getOrNull() != null) stopKoin()
        val koin = startKoin { modules(appModule, platformModule) }.koin

        assertNotNull(koin.get<PaintDataArchiveGateway>())
        assertNotNull(koin.get<PaintDraftRepository>())
        assertNotNull(koin.get<ExportPaintDataUseCase>())
        assertNotNull(koin.get<ImportPaintDataUseCase>())
        assertNotNull(koin.get<PreviewPaintDataImportUseCase>())
    }
}
