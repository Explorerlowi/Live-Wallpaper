package com.example.livewallpaper.gallery.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.livewallpaper.gallery.data.MediaStoreRepository
import com.example.livewallpaper.gallery.model.Album
import com.example.livewallpaper.gallery.model.MediaItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 图库浏览器的 UI 状态
 */
data class GalleryUiState(
    val isLoading: Boolean = true,
    val albums: List<Album> = emptyList(),
    val allImagesAlbum: Album? = null,
    val currentAlbum: Album? = null,
    val images: List<MediaItem> = emptyList(),
    val selectedImages: Set<Uri> = emptySet(),
    val error: String? = null,
    // 相册列表滚动位置
    val albumListScrollIndex: Int = 0,
    val albumListScrollOffset: Int = 0
)

/**
 * 图库页面类型
 */
sealed class GalleryPage {
    data object AlbumList : GalleryPage()
    data class ImageGrid(val album: Album) : GalleryPage()
}

/**
 * 图库浏览器 ViewModel
 */
class GalleryViewModel(
    private val repository: MediaStoreRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(GalleryUiState())
    val uiState: StateFlow<GalleryUiState> = _uiState.asStateFlow()

    private val _currentPage = MutableStateFlow<GalleryPage>(GalleryPage.AlbumList)
    val currentPage: StateFlow<GalleryPage> = _currentPage.asStateFlow()

    /**
     * 加载相册列表
     */
    fun loadAlbums() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val albums = repository.getAlbums()
                val allImagesAlbum = repository.getAllImagesAlbum()
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        albums = albums,
                        allImagesAlbum = allImagesAlbum
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "加载相册失败"
                    )
                }
            }
        }
    }

    /**
     * 打开指定相册
     */
    fun openAlbum(album: Album) {
        _currentPage.value = GalleryPage.ImageGrid(album)
        loadImages(album)
    }

    /**
     * 返回相册列表
     */
    fun backToAlbums() {
        _currentPage.value = GalleryPage.AlbumList
        _uiState.update {
            it.copy(
                currentAlbum = null,
                images = emptyList()
            )
        }
    }
    
    /**
     * 保存相册列表滚动位置
     * @param scrollIndex 第一个可见项的索引
     * @param scrollOffset 第一个可见项的偏移量
     */
    fun saveAlbumListScrollPosition(scrollIndex: Int, scrollOffset: Int) {
        _uiState.update {
            it.copy(
                albumListScrollIndex = scrollIndex,
                albumListScrollOffset = scrollOffset
            )
        }
    }

    /**
     * 加载指定相册的图片
     */
    private fun loadImages(album: Album) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, currentAlbum = album) }
            try {
                // bucketId 为 -1 表示"所有图片"
                val bucketId = if (album.id == -1L) null else album.id
                val images = repository.getImages(bucketId)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        images = images
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "加载图片失败"
                    )
                }
            }
        }
    }

    /**
     * 切换图片选中状态
     */
    fun toggleImageSelection(uri: Uri) {
        _uiState.update { state ->
            val newSelection = state.selectedImages.toMutableSet()
            if (newSelection.contains(uri)) {
                newSelection.remove(uri)
            } else {
                newSelection.add(uri)
            }
            state.copy(selectedImages = newSelection)
        }
    }

    /**
     * 全选当前相册的图片
     */
    fun selectAll() {
        _uiState.update { state ->
            val allUris = state.images.map { it.uri }.toSet()
            state.copy(selectedImages = allUris)
        }
    }

    /**
     * 取消全选
     */
    fun clearSelection() {
        _uiState.update { it.copy(selectedImages = emptySet()) }
    }

    /**
     * 获取选中的图片 URI 列表
     */
    fun getSelectedUris(): List<Uri> {
        return _uiState.value.selectedImages.toList()
    }

    /**
     * 确认选择并返回
     */
    fun confirmSelection(): List<Uri> {
        val selected = getSelectedUris()
        clearSelection()
        return selected
    }
}

