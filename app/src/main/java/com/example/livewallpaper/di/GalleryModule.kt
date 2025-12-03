package com.example.livewallpaper.di

import com.example.livewallpaper.gallery.data.MediaStoreRepository
import com.example.livewallpaper.gallery.viewmodel.GalleryViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * 图库浏览器 Koin 模块
 */
val galleryModule = module {
    // MediaStore 仓库
    single { MediaStoreRepository(androidContext()) }
    
    // 图库 ViewModel（使用 factory，按需创建）
    factory { GalleryViewModel(get()) }
}

