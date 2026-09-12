package com.example.livewallpaper.feature.dynamicwallpaper.domain.model

/**
 * 壁纸库文档的纯函数操作，负责迁移、投影与增删改。
 */
object WallpaperLibraryOperations {

    /**
     * 创建只含一个空默认库的文档。
     *
     * @param defaultLibraryName 默认库名称
     * @param generateId 标识生成器
     * @return 全新文档
     */
    fun emptyDocument(
        defaultLibraryName: String,
        generateId: () -> String
    ): WallpaperLibraryDocument {
        val libraryId = generateId()
        return WallpaperLibraryDocument(
            activeLibraryId = libraryId,
            libraries = listOf(
                WallpaperLibrary(id = libraryId, name = defaultLibraryName)
            )
        )
    }

    /**
     * 将旧版扁平 [WallpaperConfig.imageUris] 迁入默认库。
     *
     * @param config 旧配置，图片列表与裁剪参数作为源数据
     * @param defaultLibraryName 默认库名称
     * @param generateId 标识生成器
     * @return 迁移后的文档
     */
    fun migrateFromConfig(
        config: WallpaperConfig,
        defaultLibraryName: String,
        generateId: () -> String
    ): WallpaperLibraryDocument {
        val libraryId = generateId()
        val items = config.imageUris.distinct().map { uri ->
            WallpaperItem(
                id = generateId(),
                uri = uri,
                cropParams = config.imageCropParams[uri] ?: ImageCropParams()
            )
        }
        return WallpaperLibraryDocument(
            activeLibraryId = libraryId,
            libraries = listOf(
                WallpaperLibrary(
                    id = libraryId,
                    name = defaultLibraryName,
                    itemIds = items.map { it.id }
                )
            ),
            items = items
        )
    }

    /**
     * 去掉无效引用与未被任何库使用的条目，并校正激活库。
     *
     * @param document 原始文档
     * @return 清理后的文档
     */
    fun sanitize(document: WallpaperLibraryDocument): WallpaperLibraryDocument {
        val itemById = document.items.associateBy { it.id }
        val libraries = document.libraries.map { library ->
            library.copy(itemIds = library.itemIds.distinct().filter { it in itemById })
        }
        val referencedIds = libraries.flatMap { it.itemIds }.toSet()
        val items = document.items.filter { it.id in referencedIds }.distinctBy { it.id }
        val activeLibraryId = when {
            libraries.any { it.id == document.activeLibraryId } -> document.activeLibraryId
            libraries.isNotEmpty() -> libraries.first().id
            else -> document.activeLibraryId
        }
        return document.copy(
            activeLibraryId = activeLibraryId,
            libraries = libraries,
            items = items
        )
    }

    /**
     * 投影当前激活库的图片 URI 列表，保持库内顺序。
     *
     * @param document 壁纸库文档
     * @return 激活库图片 URI
     */
    fun projectImageUris(document: WallpaperLibraryDocument): List<String> {
        val library = activeLibrary(document) ?: return emptyList()
        return projectImageUris(document, library.id)
    }

    /**
     * 投影指定库的图片 URI 列表，保持库内顺序。
     *
     * @param document 壁纸库文档
     * @param libraryId 目标库
     * @return 该库图片 URI，库不存在时为空
     */
    fun projectImageUris(document: WallpaperLibraryDocument, libraryId: String): List<String> {
        val itemsById = document.items.associateBy { it.id }
        val library = document.libraries.find { it.id == libraryId } ?: return emptyList()
        return library.itemIds.mapNotNull { itemsById[it]?.uri }
    }

    /**
     * 按库 ID 投影所有库的图片 URI 列表，供库列表与库内浏览界面使用。
     *
     * @param document 壁纸库文档
     * @return 库 ID 到图片 URI 列表的映射
     */
    fun projectImageUrisByLibrary(document: WallpaperLibraryDocument): Map<String, List<String>> {
        val itemsById = document.items.associateBy { it.id }
        return document.libraries.associate { library ->
            library.id to library.itemIds.mapNotNull { itemsById[it]?.uri }
        }
    }

    /**
     * 投影全部图片中已自定义的裁剪参数，不限激活库。
     *
     * @param document 壁纸库文档
     * @return URI 到裁剪参数的映射（仅包含非默认值）
     */
    fun projectAllCropParams(document: WallpaperLibraryDocument): Map<String, ImageCropParams> {
        return document.items
            .filter { it.cropParams.isCustomized() }
            .associate { it.uri to it.cropParams }
    }

    /**
     * 投影激活库图片的裁剪参数。
     *
     * @param document 壁纸库文档
     * @return URI 到裁剪参数的映射
     */
    fun projectCropParams(document: WallpaperLibraryDocument): Map<String, ImageCropParams> {
        val activeUris = projectImageUris(document).toSet()
        return document.items
            .filter { it.uri in activeUris && it.cropParams.isCustomized() }
            .associate { it.uri to it.cropParams }
    }

    /** 仅当用户实际调整过裁剪时才视为自定义，默认值不写入投影（UI 以此判断是否显示「已裁剪」标记） */
    private fun ImageCropParams.isCustomized(): Boolean = this != ImageCropParams()

    /**
     * 将全局设置与激活库投影合并为引擎/UI 使用的 [WallpaperConfig]。
     *
     * @param settings 全局设置（间隔、播放模式等）
     * @param document 壁纸库文档
     * @return 带有激活库图片列表的配置
     */
    fun projectConfig(
        settings: WallpaperConfig,
        document: WallpaperLibraryDocument
    ): WallpaperConfig {
        return settings.copy(
            imageUris = projectImageUris(document),
            imageCropParams = projectCropParams(document)
        )
    }

    /**
     * 去掉配置中的图片投影字段，仅保留全局设置以便单独持久化。
     *
     * @param config 可能带有投影字段的配置
     * @return 不含图片列表的设置副本
     */
    fun withoutLibraryProjection(config: WallpaperConfig): WallpaperConfig {
        return config.copy(imageUris = emptyList(), imageCropParams = emptyMap())
    }

    /**
     * 生成 UI 用的库摘要列表。
     *
     * @param document 壁纸库文档
     * @return 按库顺序排列的摘要
     */
    fun toSummaries(document: WallpaperLibraryDocument): List<WallpaperLibrarySummary> {
        return document.libraries.map { library ->
            WallpaperLibrarySummary(
                id = library.id,
                name = library.name,
                imageCount = library.itemIds.size,
                isActive = library.id == document.activeLibraryId
            )
        }
    }

    /**
     * 将图片加入指定库。已存在的 URI 只增加引用，不新建条目。
     *
     * @param document 当前文档
     * @param libraryId 目标库
     * @param uris 要加入的图片地址
     * @param generateId 新条目标识生成器
     * @return 更新后的文档
     */
    fun addImagesToLibrary(
        document: WallpaperLibraryDocument,
        libraryId: String,
        uris: List<String>,
        generateId: () -> String
    ): WallpaperLibraryDocument {
        val library = document.libraries.find { it.id == libraryId } ?: return document
        val itemsByUri = document.items.associateBy { it.uri }.toMutableMap()
        val items = document.items.toMutableList()
        val itemIds = library.itemIds.toMutableList()
        uris.distinct().forEach { uri ->
            if (uri.isBlank()) return@forEach
            val item = itemsByUri[uri] ?: WallpaperItem(id = generateId(), uri = uri).also {
                items += it
                itemsByUri[uri] = it
            }
            if (item.id !in itemIds) {
                itemIds += item.id
            }
        }
        return document.copy(
            items = items,
            libraries = replaceLibrary(document.libraries, library.copy(itemIds = itemIds))
        )
    }

    /**
     * 从指定库移除图片；若再无库引用则删除条目。
     *
     * @param document 当前文档
     * @param libraryId 目标库
     * @param uris 要移除的图片地址
     * @return 更新后的文档
     */
    fun removeImagesFromLibrary(
        document: WallpaperLibraryDocument,
        libraryId: String,
        uris: Collection<String>
    ): WallpaperLibraryDocument {
        val library = document.libraries.find { it.id == libraryId } ?: return document
        val uriSet = uris.toSet()
        val itemsById = document.items.associateBy { it.id }
        val remainingIds = library.itemIds.filter { id -> itemsById[id]?.uri !in uriSet }
        return sanitize(
            document.copy(
                libraries = replaceLibrary(document.libraries, library.copy(itemIds = remainingIds))
            )
        )
    }

    /**
     * 清空指定库中的全部图片引用。
     *
     * @param document 当前文档
     * @param libraryId 目标库
     * @return 更新后的文档
     */
    fun removeAllImagesFromLibrary(
        document: WallpaperLibraryDocument,
        libraryId: String
    ): WallpaperLibraryDocument {
        val library = document.libraries.find { it.id == libraryId } ?: return document
        return sanitize(
            document.copy(
                libraries = replaceLibrary(document.libraries, library.copy(itemIds = emptyList()))
            )
        )
    }

    /**
     * 按 URI 列表重排指定库的图片顺序，未知 URI 会被忽略，未出现的原图追加在末尾。
     *
     * @param document 当前文档
     * @param libraryId 目标库
     * @param uris 期望顺序
     * @return 更新后的文档
     */
    fun updateLibraryImageOrder(
        document: WallpaperLibraryDocument,
        libraryId: String,
        uris: List<String>
    ): WallpaperLibraryDocument {
        val library = document.libraries.find { it.id == libraryId } ?: return document
        val itemsById = document.items.associateBy { it.id }
        val currentItems = library.itemIds.mapNotNull { itemsById[it] }
        if (currentItems.isEmpty()) return document
        val itemsByUri = currentItems.associateBy { it.uri }
        val desiredUris = uris.distinct().filter { it in itemsByUri }
        if (desiredUris.isEmpty()) return document
        val desiredSet = desiredUris.toSet()
        val remaining = currentItems.map { it.uri }.filter { it !in desiredSet }
        val newItemIds = (desiredUris + remaining).mapNotNull { itemsByUri[it]?.id }
        if (newItemIds == library.itemIds) return document
        return document.copy(
            libraries = replaceLibrary(document.libraries, library.copy(itemIds = newItemIds))
        )
    }

    /**
     * 更新某张图片的裁剪参数，所有引用该图的库共享结果。
     *
     * @param document 当前文档
     * @param uri 图片地址
     * @param params 新的裁剪参数
     * @return 更新后的文档
     */
    fun setItemCropParams(
        document: WallpaperLibraryDocument,
        uri: String,
        params: ImageCropParams
    ): WallpaperLibraryDocument {
        if (document.items.none { it.uri == uri }) return document
        return document.copy(
            items = document.items.map { item ->
                if (item.uri == uri) item.copy(cropParams = params) else item
            }
        )
    }

    /**
     * 新建壁纸库并切换为激活库。
     *
     * @param document 当前文档
     * @param name 库名称，空白则不创建
     * @param generateId 库标识生成器
     * @return 更新后的文档
     */
    fun createLibrary(
        document: WallpaperLibraryDocument,
        name: String,
        generateId: () -> String
    ): WallpaperLibraryDocument {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return document
        val libraryId = generateId()
        return document.copy(
            activeLibraryId = libraryId,
            libraries = document.libraries + WallpaperLibrary(id = libraryId, name = trimmed)
        )
    }

    /**
     * 重命名指定壁纸库。
     *
     * @param document 当前文档
     * @param libraryId 目标库
     * @param name 新名称，空白则不修改
     * @return 更新后的文档
     */
    fun renameLibrary(
        document: WallpaperLibraryDocument,
        libraryId: String,
        name: String
    ): WallpaperLibraryDocument {
        val trimmed = name.trim()
        val library = document.libraries.find { it.id == libraryId } ?: return document
        if (trimmed.isEmpty() || trimmed == library.name) return document
        return document.copy(
            libraries = replaceLibrary(document.libraries, library.copy(name = trimmed))
        )
    }

    /**
     * 删除指定壁纸库。最后一个库不可删除；若删除的是激活库则切到剩余第一个。
     *
     * @param document 当前文档
     * @param libraryId 目标库
     * @return 更新后的文档
     */
    fun deleteLibrary(
        document: WallpaperLibraryDocument,
        libraryId: String
    ): WallpaperLibraryDocument {
        if (document.libraries.size <= 1) return document
        if (document.libraries.none { it.id == libraryId }) return document
        val remaining = document.libraries.filter { it.id != libraryId }
        val activeLibraryId = if (document.activeLibraryId == libraryId) {
            remaining.first().id
        } else {
            document.activeLibraryId
        }
        return sanitize(document.copy(activeLibraryId = activeLibraryId, libraries = remaining))
    }

    /**
     * 切换激活库。
     *
     * @param document 当前文档
     * @param libraryId 目标库
     * @return 更新后的文档
     */
    fun setActiveLibrary(
        document: WallpaperLibraryDocument,
        libraryId: String
    ): WallpaperLibraryDocument {
        if (document.libraries.none { it.id == libraryId }) return document
        if (document.activeLibraryId == libraryId) return document
        return document.copy(activeLibraryId = libraryId)
    }

    private fun activeLibrary(document: WallpaperLibraryDocument): WallpaperLibrary? {
        return document.libraries.find { it.id == document.activeLibraryId }
            ?: document.libraries.firstOrNull()
    }

    private fun replaceLibrary(
        libraries: List<WallpaperLibrary>,
        updated: WallpaperLibrary
    ): List<WallpaperLibrary> {
        return libraries.map { library ->
            if (library.id == updated.id) updated else library
        }
    }
}
