package com.example.livewallpaper.gallery.model

import android.net.Uri

/**
 * 相册数据模型
 * @param id 相册ID（bucket_id）
 * @param name 相册名称
 * @param coverUri 封面图片URI
 * @param count 相册中的图片数量
 */
data class Album(
    val id: Long,
    val name: String,
    val coverUri: Uri,
    val count: Int
)

/**
 * 媒体项数据模型
 * @param id 媒体ID
 * @param uri 媒体URI
 * @param displayName 文件名
 * @param dateAdded 添加时间（秒）
 * @param bucketId 所属相册ID
 * @param bucketName 所属相册名称
 */
data class MediaItem(
    val id: Long,
    val uri: Uri,
    val displayName: String,
    val dateAdded: Long,
    val bucketId: Long,
    val bucketName: String
)

