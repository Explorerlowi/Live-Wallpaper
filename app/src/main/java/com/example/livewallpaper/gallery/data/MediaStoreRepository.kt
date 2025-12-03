package com.example.livewallpaper.gallery.data

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import com.example.livewallpaper.gallery.model.Album
import com.example.livewallpaper.gallery.model.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * MediaStore 数据仓库
 * 用于查询设备上的图片和相册
 */
class MediaStoreRepository(private val context: Context) {

    private val contentResolver: ContentResolver = context.contentResolver

    /**
     * 获取所有相册列表
     */
    suspend fun getAlbums(): List<Album> = withContext(Dispatchers.IO) {
        val albums = mutableMapOf<Long, Album>()
        
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.BUCKET_ID,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Images.Media.DATE_ADDED
        )

        // 按日期降序排列，这样第一张就是最新的（作为封面）
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        contentResolver.query(
            collection,
            projection,
            null,
            null,
            sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val bucketIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_ID)
            val bucketNameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val bucketId = cursor.getLong(bucketIdColumn)
                val bucketName = cursor.getString(bucketNameColumn) ?: "未知相册"

                val contentUri = ContentUris.withAppendedId(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    id
                )

                if (albums.containsKey(bucketId)) {
                    // 相册已存在，增加计数
                    val existing = albums[bucketId]!!
                    albums[bucketId] = existing.copy(count = existing.count + 1)
                } else {
                    // 新相册，第一张图片作为封面
                    albums[bucketId] = Album(
                        id = bucketId,
                        name = bucketName,
                        coverUri = contentUri,
                        count = 1
                    )
                }
            }
        }

        // 按图片数量降序排列
        albums.values.sortedByDescending { it.count }
    }

    /**
     * 获取指定相册中的所有图片
     * @param bucketId 相册ID，如果为null则获取所有图片
     */
    suspend fun getImages(bucketId: Long? = null): List<MediaItem> = withContext(Dispatchers.IO) {
        val images = mutableListOf<MediaItem>()

        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.BUCKET_ID,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME
        )

        val selection = if (bucketId != null) {
            "${MediaStore.Images.Media.BUCKET_ID} = ?"
        } else {
            null
        }

        val selectionArgs = if (bucketId != null) {
            arrayOf(bucketId.toString())
        } else {
            null
        }

        // 按日期降序排列
        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        contentResolver.query(
            collection,
            projection,
            selection,
            selectionArgs,
            sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val displayNameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val dateAddedColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            val bucketIdColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_ID)
            val bucketNameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val displayName = cursor.getString(displayNameColumn) ?: ""
                val dateAdded = cursor.getLong(dateAddedColumn)
                val imgBucketId = cursor.getLong(bucketIdColumn)
                val imgBucketName = cursor.getString(bucketNameColumn) ?: ""

                val contentUri = ContentUris.withAppendedId(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    id
                )

                images.add(
                    MediaItem(
                        id = id,
                        uri = contentUri,
                        displayName = displayName,
                        dateAdded = dateAdded,
                        bucketId = imgBucketId,
                        bucketName = imgBucketName
                    )
                )
            }
        }

        images
    }

    /**
     * 获取"所有图片"虚拟相册
     */
    suspend fun getAllImagesAlbum(): Album? = withContext(Dispatchers.IO) {
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATE_ADDED
        )

        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        var count = 0
        var coverUri: android.net.Uri? = null

        contentResolver.query(
            collection,
            projection,
            null,
            null,
            sortOrder
        )?.use { cursor ->
            count = cursor.count
            if (cursor.moveToFirst()) {
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val id = cursor.getLong(idColumn)
                coverUri = ContentUris.withAppendedId(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    id
                )
            }
        }

        coverUri?.let {
            Album(
                id = -1L, // 特殊ID表示"所有图片"
                name = "所有图片",
                coverUri = it,
                count = count
            )
        }
    }
}

