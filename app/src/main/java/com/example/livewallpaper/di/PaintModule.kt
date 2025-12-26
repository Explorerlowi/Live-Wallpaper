package com.example.livewallpaper.di

import com.example.livewallpaper.paint.viewmodel.AndroidPaintViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

/**
 * AI 绘画模块 DI 配置
 * 注意：PaintRepository 已在 shared 模块的 appModule 中注册
 */
val paintModule = module {
    viewModel { AndroidPaintViewModel(androidContext(), get()) }
}
