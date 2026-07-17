package com.example.livewallpaper.di

import com.example.livewallpaper.feature.aipaint.domain.repository.PaintDataArchiveGateway
import com.example.livewallpaper.feature.aipaint.domain.repository.PaintDraftRepository
import com.example.livewallpaper.feature.aipaint.domain.usecase.ExportPaintDataUseCase
import com.example.livewallpaper.feature.aipaint.domain.usecase.ImportPaintDataUseCase
import com.example.livewallpaper.feature.aipaint.domain.usecase.PreviewPaintDataImportUseCase
import com.example.livewallpaper.paint.data.AndroidPaintDataArchiveGateway
import com.example.livewallpaper.paint.data.AndroidPaintDraftRepository
import com.example.livewallpaper.paint.viewmodel.AndroidPaintViewModel
import com.example.livewallpaper.paint.viewmodel.PaintDataTransferViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

/**
 * AI 绘画模块 DI 配置
 * 注意：PaintRepository 已在 shared 模块的 appModule 中注册
 */
val paintModule = module {
    single<PaintDataArchiveGateway> { AndroidPaintDataArchiveGateway(androidContext()) }
    single<PaintDraftRepository> { AndroidPaintDraftRepository(androidContext()) }
    factory { ExportPaintDataUseCase(get(), get(), get()) }
    factory { ImportPaintDataUseCase(get(), get(), get()) }
    factory { PreviewPaintDataImportUseCase(get()) }
    viewModel { AndroidPaintViewModel(androidContext(), get(), get()) }
    viewModel { PaintDataTransferViewModel(get(), get(), get()) }
}
