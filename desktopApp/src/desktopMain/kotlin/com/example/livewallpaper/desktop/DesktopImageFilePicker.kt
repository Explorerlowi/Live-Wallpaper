package com.example.livewallpaper.desktop

import com.sun.jna.Native
import com.sun.jna.Platform
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.WString
import com.sun.jna.platform.win32.COM.COMUtils
import com.sun.jna.platform.win32.COM.Unknown
import com.sun.jna.platform.win32.Guid
import com.sun.jna.platform.win32.Ole32
import com.sun.jna.platform.win32.Shell32
import com.sun.jna.platform.win32.WTypes
import com.sun.jna.platform.win32.WinNT
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.PointerByReference
import com.sun.jna.win32.W32APIOptions
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import javax.swing.JFileChooser
import javax.swing.SwingUtilities
import javax.swing.UIManager
import javax.swing.filechooser.FileNameExtensionFilter
import javax.swing.filechooser.FileSystemView

private data class DesktopFileDialogFilter(
    val description: String,
    val pattern: String,
    val extensions: List<String>,
)

/** 桌面端统一文件选择入口；Windows 使用系统原生 IFileDialog，其他平台使用 Swing 回退。 */
internal object DesktopFilePicker {
    private val imageExtensions = arrayOf("png", "jpg", "jpeg", "webp", "gif", "bmp")

    /**
     * 选择一个或多个图片文件。
     *
     * @param title 文件选择窗口标题。
     * @param isSupportedImageName 用于最终校验文件名的规则。
     * @return 用户选择且通过校验的绝对路径。
     */
    fun pickImagePaths(title: String, isSupportedImageName: (String) -> Boolean): List<String> {
        if (Platform.isWindows()) {
            return pickImagePathsWithWindowsDialog(title, isSupportedImageName).orEmpty()
        }
        return pickImagePathsWithSwing(title, isSupportedImageName)
    }

    /**
     * 选择一个目录。
     *
     * @param title 目录选择窗口标题。
     * @param initialPath 初始目录，不可用时回退到图片目录。
     * @return 用户选择的绝对路径，取消时返回空。
     */
    fun pickDirectoryPath(title: String, initialPath: String? = null): String? {
        val initialDirectory = initialPath
            ?.let(::File)
            ?.takeIf { it.isDirectory }
            ?: defaultImageDirectory()
        if (Platform.isWindows()) {
            pickDirectoryPathWithWindowsDialog(title, initialDirectory)?.let { return it.getOrNull() }
        }
        return pickDirectoryPathWithSwing(title, initialDirectory)
    }

    /**
     * 选择图片保存位置。
     *
     * @param title 文件选择窗口标题。
     * @param defaultFileName 默认文件名。
     * @return 用户选择的绝对路径，取消时返回空。
     */
    fun pickSaveImagePath(title: String, defaultFileName: String): String? {
        if (Platform.isWindows()) {
            pickSaveImagePathWithWindowsDialog(title, defaultFileName)?.let { return it.getOrNull() }
        }
        return pickSaveImagePathWithSwing(title, defaultFileName)
    }

    /**
     * 使用与图片选择一致的系统文件选择器打开 JSON 文件。
     *
     * @param title 文件选择窗口标题。
     * @param filterDescription JSON 文件类型的本地化描述。
     * @return 用户选择的绝对路径，取消时返回空。
     */
    fun pickJsonPath(title: String, filterDescription: String): String? {
        val filter = jsonFilter(filterDescription)
        if (Platform.isWindows()) {
            pickFilePathWithWindowsDialog(title, filter)?.let { return it.getOrNull() }
        }
        return pickFilePathWithSwing(title, filter)
    }

    /**
     * 使用与图片另存为一致的系统文件选择器保存 JSON 文件。
     *
     * @param title 文件选择窗口标题。
     * @param defaultFileName 默认文件名。
     * @param filterDescription JSON 文件类型的本地化描述。
     * @return 用户选择的绝对路径，取消时返回空。
     */
    fun pickSaveJsonPath(title: String, defaultFileName: String, filterDescription: String): String? {
        val filter = jsonFilter(filterDescription)
        if (Platform.isWindows()) {
            pickSaveFilePathWithWindowsDialog(title, defaultFileName, filter)?.let { return it.getOrNull() }
        }
        return pickSaveFilePathWithSwing(title, defaultFileName, filter)
    }

    /** Selects one existing ZIP painting data backup. */
    fun pickZipPath(title: String, filterDescription: String): String? {
        val filter = zipFilter(filterDescription)
        if (Platform.isWindows()) {
            pickFilePathWithWindowsDialog(title, filter)?.let { return it.getOrNull() }
        }
        return pickFilePathWithSwing(title, filter)
    }

    /** Selects a destination for a ZIP painting data backup. */
    fun pickSaveZipPath(title: String, defaultFileName: String, filterDescription: String): String? {
        val filter = zipFilter(filterDescription)
        if (Platform.isWindows()) {
            pickSaveFilePathWithWindowsDialog(title, defaultFileName, filter)?.let { return it.getOrNull() }
        }
        return pickSaveFilePathWithSwing(title, defaultFileName, filter)
    }

    private fun pickImagePathsWithSwing(
        title: String,
        isSupportedImageName: (String) -> Boolean
    ): List<String> {
        return runCatching {
            val result = AtomicReference<List<String>>(emptyList())
            val task = Runnable {
                runCatching {
                    UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName())
                }
                val chooser = JFileChooser(defaultImageDirectory()).apply {
                    dialogTitle = title
                    fileSelectionMode = JFileChooser.FILES_ONLY
                    isMultiSelectionEnabled = true
                    setAcceptAllFileFilterUsed(false)
                    fileFilter = FileNameExtensionFilter(
                        "Images (*.png, *.jpg, *.jpeg, *.webp, *.gif, *.bmp)",
                        *imageExtensions
                    )
                }
                if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                    result.set(
                        chooser.selectedFiles
                            .filter { it.isFile && isSupportedImageName(it.name) }
                            .map { it.absolutePath }
                    )
                }
            }
            if (SwingUtilities.isEventDispatchThread()) {
                task.run()
            } else {
                SwingUtilities.invokeAndWait(task)
            }
            result.get()
        }.getOrElse {
            pickImagePathsWithAwtFallback(title, isSupportedImageName)
        }
    }

    private fun pickImagePathsWithWindowsDialog(
        title: String,
        isSupportedImageName: (String) -> Boolean
    ): List<String>? {
        val result = AtomicReference<Result<List<String>>?>()
        val thread = Thread {
            result.set(runCatching { WindowsFileOpenDialog.show(title, isSupportedImageName) })
        }.apply {
            name = "windows-file-open-dialog"
            isDaemon = true
        }
        thread.start()
        thread.join()
        return result.get()?.getOrNull()
    }

    private fun pickDirectoryPathWithWindowsDialog(title: String, initialDirectory: File): Result<String?>? {
        val result = AtomicReference<Result<String?>?>()
        val thread = Thread {
            result.set(runCatching { WindowsFileOpenDialog.showFolder(title, initialDirectory) })
        }.apply {
            name = "windows-folder-open-dialog"
            isDaemon = true
        }
        thread.start()
        thread.join()
        return result.get()
    }

    private fun pickSaveImagePathWithWindowsDialog(title: String, defaultFileName: String): Result<String?>? {
        val result = AtomicReference<Result<String?>?>()
        val thread = Thread {
            result.set(runCatching { WindowsFileOpenDialog.showSave(title, defaultFileName) })
        }.apply {
            name = "windows-file-save-dialog"
            isDaemon = true
        }
        thread.start()
        thread.join()
        return result.get()
    }

    private fun pickFilePathWithWindowsDialog(
        title: String,
        filter: DesktopFileDialogFilter,
    ): Result<String?>? {
        val result = AtomicReference<Result<String?>?>()
        val thread = Thread {
            result.set(runCatching { WindowsFileOpenDialog.showFile(title, defaultFileDirectory(), filter) })
        }.apply {
            name = "windows-file-open-dialog"
            isDaemon = true
        }
        thread.start()
        thread.join()
        return result.get()
    }

    private fun pickSaveFilePathWithWindowsDialog(
        title: String,
        defaultFileName: String,
        filter: DesktopFileDialogFilter,
    ): Result<String?>? {
        val result = AtomicReference<Result<String?>?>()
        val thread = Thread {
            result.set(
                runCatching {
                    WindowsFileOpenDialog.showSave(title, defaultFileName, defaultFileDirectory(), filter)
                },
            )
        }.apply {
            name = "windows-file-save-dialog"
            isDaemon = true
        }
        thread.start()
        thread.join()
        return result.get()
    }

    private fun pickDirectoryPathWithSwing(title: String, initialDirectory: File): String? {
        return runCatching {
            val result = AtomicReference<String?>(null)
            val task = Runnable {
                runCatching {
                    UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName())
                }
                val chooser = JFileChooser(initialDirectory).apply {
                    dialogTitle = title
                    fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
                    isMultiSelectionEnabled = false
                    setAcceptAllFileFilterUsed(false)
                }
                if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                    result.set(chooser.selectedFile?.absolutePath)
                }
            }
            if (SwingUtilities.isEventDispatchThread()) {
                task.run()
            } else {
                SwingUtilities.invokeAndWait(task)
            }
            result.get()
        }.getOrNull()
    }

    private fun pickSaveImagePathWithSwing(title: String, defaultFileName: String): String? {
        return runCatching {
            val result = AtomicReference<String?>(null)
            val task = Runnable {
                runCatching {
                    UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName())
                }
                val chooser = JFileChooser(defaultImageDirectory()).apply {
                    dialogTitle = title
                    fileSelectionMode = JFileChooser.FILES_ONLY
                    isMultiSelectionEnabled = false
                    selectedFile = File(defaultFileName)
                    setAcceptAllFileFilterUsed(false)
                    fileFilter = FileNameExtensionFilter(
                        "Images (*.png, *.jpg, *.jpeg, *.webp, *.bmp)",
                        "png", "jpg", "jpeg", "webp", "bmp"
                    )
                }
                if (chooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
                    result.set(chooser.selectedFile?.absolutePath)
                }
            }
            if (SwingUtilities.isEventDispatchThread()) {
                task.run()
            } else {
                SwingUtilities.invokeAndWait(task)
            }
            result.get()
        }.getOrNull()
    }

    private fun pickFilePathWithSwing(title: String, filter: DesktopFileDialogFilter): String? = runCatching {
        val result = AtomicReference<String?>(null)
        val task = Runnable {
            runCatching { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()) }
            val chooser = JFileChooser(defaultFileDirectory()).apply {
                dialogTitle = title
                fileSelectionMode = JFileChooser.FILES_ONLY
                isMultiSelectionEnabled = false
                setAcceptAllFileFilterUsed(false)
                fileFilter = FileNameExtensionFilter(filter.description, *filter.extensions.toTypedArray())
            }
            if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                result.set(chooser.selectedFile?.takeIf { it.isFile }?.absolutePath)
            }
        }
        if (SwingUtilities.isEventDispatchThread()) task.run() else SwingUtilities.invokeAndWait(task)
        result.get()
    }.getOrNull()

    private fun pickSaveFilePathWithSwing(
        title: String,
        defaultFileName: String,
        filter: DesktopFileDialogFilter,
    ): String? = runCatching {
        val result = AtomicReference<String?>(null)
        val task = Runnable {
            runCatching { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()) }
            val chooser = JFileChooser(defaultFileDirectory()).apply {
                dialogTitle = title
                fileSelectionMode = JFileChooser.FILES_ONLY
                isMultiSelectionEnabled = false
                selectedFile = File(defaultFileName)
                setAcceptAllFileFilterUsed(false)
                fileFilter = FileNameExtensionFilter(filter.description, *filter.extensions.toTypedArray())
            }
            if (chooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
                result.set(
                    chooser.selectedFile?.let { file ->
                        val extension = filter.extensions.first()
                        if (file.extension.equals(extension, ignoreCase = true)) {
                            file
                        } else {
                            File(file.path + ".$extension")
                        }
                    }?.absolutePath,
                )
            }
        }
        if (SwingUtilities.isEventDispatchThread()) task.run() else SwingUtilities.invokeAndWait(task)
        result.get()
    }.getOrNull()

    private fun defaultImageDirectory(): File {
        val pictures = File(System.getProperty("user.home"), "Pictures")
        return when {
            pictures.isDirectory -> pictures
            else -> FileSystemView.getFileSystemView().defaultDirectory
        }
    }

    private fun defaultFileDirectory(): File = FileSystemView.getFileSystemView().defaultDirectory

    private fun jsonFilter(description: String): DesktopFileDialogFilter = DesktopFileDialogFilter(
        description = description,
        pattern = "*.json",
        extensions = listOf("json"),
    )

    private fun zipFilter(description: String): DesktopFileDialogFilter = DesktopFileDialogFilter(
        description = description,
        pattern = "*.zip",
        extensions = listOf("zip"),
    )

    private fun pickImagePathsWithAwtFallback(
        title: String,
        isSupportedImageName: (String) -> Boolean
    ): List<String> {
        val dialog = FileDialog(null as Frame?, title, FileDialog.LOAD).apply {
            isMultipleMode = true
            filenameFilter = java.io.FilenameFilter { _, name -> isSupportedImageName(name) }
        }
        dialog.isVisible = true
        return dialog.files.map { it.absolutePath }
    }
}

private object WindowsFileOpenDialog {
    private val imageFilter = DesktopFileDialogFilter(
        description = "图片文件 (*.png;*.jpg;*.jpeg;*.webp;*.gif;*.bmp)",
        pattern = "*.png;*.jpg;*.jpeg;*.webp;*.gif;*.bmp",
        extensions = listOf("png", "jpg", "jpeg", "webp", "gif", "bmp"),
    )
    private val clsidFileOpenDialog = Guid.CLSID("{DC1C5A9C-E88A-4DDE-A5A1-60F82A20AEF7}")
    private val clsidFileSaveDialog = Guid.CLSID("{C0B4E2F3-BA21-4773-8DBA-335EC946EB8B}")
    private val iidFileOpenDialog = Guid.IID("{D57C7288-D4AD-4768-BE02-9D969532D960}")
    private val iidFileSaveDialog = Guid.IID("{84BCCD23-5FDE-4CDB-AEA4-AF64B83D78AB}")
    private val iidShellItem = Guid.IID("{43826D1E-E718-42EE-BC55-A1E261C37BFE}")
    private const val SIGDN_FILESYSPATH = -2147123200
    private const val FOS_OVERWRITEPROMPT = 0x00000002
    private const val FOS_STRICTFILETYPES = 0x00000004
    private const val FOS_NOCHANGEDIR = 0x00000008
    private const val FOS_PICKFOLDERS = 0x00000020
    private const val FOS_FORCEFILESYSTEM = 0x00000040
    private const val FOS_ALLNONSTORAGEITEMS = 0x00000080
    private const val FOS_NOVALIDATE = 0x00000100
    private const val FOS_ALLOWMULTISELECT = 0x00000200
    private const val FOS_PATHMUSTEXIST = 0x00000800
    private const val FOS_FILEMUSTEXIST = 0x00001000

    fun show(title: String, isSupportedImageName: (String) -> Boolean): List<String> {
        val initHr = Ole32.INSTANCE.CoInitializeEx(Pointer.NULL, Ole32.COINIT_APARTMENTTHREADED)
        if (COMUtils.FAILED(initHr)) return emptyList()
        var dialog: FileOpenDialog? = null
        try {
            val dialogRef = PointerByReference()
            val createHr = Ole32.INSTANCE.CoCreateInstance(
                clsidFileOpenDialog,
                null,
                WTypes.CLSCTX_INPROC_SERVER,
                iidFileOpenDialog,
                dialogRef
            )
            if (COMUtils.FAILED(createHr)) return emptyList()
            dialog = FileOpenDialog(dialogRef.value)
            dialog.setTitle(title)
            dialog.setOptions(FOS_FORCEFILESYSTEM or FOS_FILEMUSTEXIST or FOS_PATHMUSTEXIST or FOS_ALLOWMULTISELECT)
            dialog.setFileType(imageFilter)
            dialog.setDefaultFolder(defaultImageDirectory())

            val showHr = dialog.show()
            if (COMUtils.FAILED(showHr)) return emptyList()
            val arrayPointer = dialog.getResults() ?: return emptyList()
            val array = ShellItemArray(arrayPointer)
            try {
                return array.paths()
                    .filter { isSupportedImageName(File(it).name) }
            } finally {
                array.Release()
            }
        } finally {
            dialog?.Release()
            Ole32.INSTANCE.CoUninitialize()
        }
    }

    fun showFolder(title: String, initialDirectory: File): String? {
        val initHr = Ole32.INSTANCE.CoInitializeEx(Pointer.NULL, Ole32.COINIT_APARTMENTTHREADED)
        if (COMUtils.FAILED(initHr)) return null
        var dialog: FileOpenDialog? = null
        try {
            val dialogRef = PointerByReference()
            val createHr = Ole32.INSTANCE.CoCreateInstance(
                clsidFileOpenDialog,
                null,
                WTypes.CLSCTX_INPROC_SERVER,
                iidFileOpenDialog,
                dialogRef
            )
            if (COMUtils.FAILED(createHr)) return null
            dialog = FileOpenDialog(dialogRef.value)
            dialog.setTitle(title)
            dialog.setOptions(FOS_PICKFOLDERS or FOS_FORCEFILESYSTEM or FOS_PATHMUSTEXIST)
            dialog.setDefaultFolder(initialDirectory)

            val showHr = dialog.show()
            if (COMUtils.FAILED(showHr)) return null
            val itemPointer = dialog.getResult() ?: return null
            val item = ShellItem(itemPointer)
            return try {
                item.fileSystemPath()
            } finally {
                item.Release()
            }
        } finally {
            dialog?.Release()
            Ole32.INSTANCE.CoUninitialize()
        }
    }

    fun showFile(
        title: String,
        initialDirectory: File,
        filter: DesktopFileDialogFilter,
    ): String? {
        val initHr = Ole32.INSTANCE.CoInitializeEx(Pointer.NULL, Ole32.COINIT_APARTMENTTHREADED)
        if (COMUtils.FAILED(initHr)) return null
        var dialog: FileOpenDialog? = null
        try {
            val dialogRef = PointerByReference()
            val createHr = Ole32.INSTANCE.CoCreateInstance(
                clsidFileOpenDialog,
                null,
                WTypes.CLSCTX_INPROC_SERVER,
                iidFileOpenDialog,
                dialogRef,
            )
            if (COMUtils.FAILED(createHr)) return null
            dialog = FileOpenDialog(dialogRef.value)
            dialog.setTitle(title)
            dialog.setOptions(FOS_FORCEFILESYSTEM or FOS_FILEMUSTEXIST or FOS_PATHMUSTEXIST)
            dialog.setFileType(filter)
            dialog.setDefaultFolder(initialDirectory)

            val showHr = dialog.show()
            if (COMUtils.FAILED(showHr)) return null
            val itemPointer = dialog.getResult() ?: return null
            val item = ShellItem(itemPointer)
            return try {
                item.fileSystemPath()
            } finally {
                item.Release()
            }
        } finally {
            dialog?.Release()
            Ole32.INSTANCE.CoUninitialize()
        }
    }

    fun showSave(title: String, defaultFileName: String): String? {
        return showSave(title, defaultFileName, defaultImageDirectory(), imageFilter)
    }

    fun showSave(
        title: String,
        defaultFileName: String,
        initialDirectory: File,
        filter: DesktopFileDialogFilter,
    ): String? {
        val initHr = Ole32.INSTANCE.CoInitializeEx(Pointer.NULL, Ole32.COINIT_APARTMENTTHREADED)
        if (COMUtils.FAILED(initHr)) return null
        var dialog: FileOpenDialog? = null
        try {
            val dialogRef = PointerByReference()
            val createHr = Ole32.INSTANCE.CoCreateInstance(
                clsidFileSaveDialog,
                null,
                WTypes.CLSCTX_INPROC_SERVER,
                iidFileSaveDialog,
                dialogRef
            )
            if (COMUtils.FAILED(createHr)) return null
            dialog = FileOpenDialog(dialogRef.value)
            dialog.setTitle(title)
            dialog.setOptions(FOS_OVERWRITEPROMPT or FOS_FORCEFILESYSTEM or FOS_PATHMUSTEXIST)
            dialog.setFileType(filter)
            dialog.setDefaultFolder(initialDirectory)
            dialog.setFileName(defaultFileName)
            dialog.setDefaultExtension(defaultFileName.substringAfterLast('.', filter.extensions.first()))

            val showHr = dialog.show()
            if (COMUtils.FAILED(showHr)) return null
            val itemPointer = dialog.getResult() ?: return null
            val item = ShellItem(itemPointer)
            return try {
                item.fileSystemPath()
            } finally {
                item.Release()
            }
        } finally {
            dialog?.Release()
            Ole32.INSTANCE.CoUninitialize()
        }
    }

    private fun FileOpenDialog.setFileType(filter: DesktopFileDialogFilter) {
        runCatching {
            val spec = ComDlgFilterSpec().apply {
                pszName = WString(filter.description)
                pszSpec = WString(filter.pattern)
            }
            val array = spec.toArray(1) as Array<ComDlgFilterSpec>
            array[0].pszName = spec.pszName
            array[0].pszSpec = spec.pszSpec
            array[0].write()
            setFileTypes(1, array[0].pointer)
            setFileTypeIndex(1)
        }
    }

    private fun FileOpenDialog.setDefaultFolder(directory: File) {
        if (!directory.isDirectory) return
        runCatching {
            val itemRef = PointerByReference()
            val hr = Shell32Extra.INSTANCE.SHCreateItemFromParsingName(
                WString(directory.absolutePath),
                null,
                Guid.REFIID(iidShellItem),
                itemRef
            )
            if (COMUtils.FAILED(hr)) return
            val item = Unknown(itemRef.value)
            try {
                setDefaultFolder(item.pointer)
            } finally {
                item.Release()
            }
        }
    }

    private fun defaultImageDirectory(): File {
        val pictures = File(System.getProperty("user.home"), "Pictures")
        return when {
            pictures.isDirectory -> pictures
            else -> FileSystemView.getFileSystemView().defaultDirectory
        }
    }

    private class FileOpenDialog(pointer: Pointer) : Unknown(pointer) {
        fun show(): WinNT.HRESULT =
            _invokeNativeObject(3, arrayOf(pointer, null), WinNT.HRESULT::class.java) as WinNT.HRESULT

        fun setFileTypes(count: Int, filtersPointer: Pointer): WinNT.HRESULT =
            _invokeNativeObject(4, arrayOf(pointer, count, filtersPointer), WinNT.HRESULT::class.java) as WinNT.HRESULT

        fun setFileTypeIndex(index: Int): WinNT.HRESULT =
            _invokeNativeObject(5, arrayOf(pointer, index), WinNT.HRESULT::class.java) as WinNT.HRESULT

        fun setOptions(options: Int): WinNT.HRESULT =
            _invokeNativeObject(9, arrayOf(pointer, options), WinNT.HRESULT::class.java) as WinNT.HRESULT

        fun setDefaultFolder(shellItemPointer: Pointer): WinNT.HRESULT =
            _invokeNativeObject(11, arrayOf(pointer, shellItemPointer), WinNT.HRESULT::class.java) as WinNT.HRESULT

        fun setFileName(fileName: String): WinNT.HRESULT =
            _invokeNativeObject(15, arrayOf(pointer, WString(fileName)), WinNT.HRESULT::class.java) as WinNT.HRESULT

        fun setTitle(title: String): WinNT.HRESULT =
            _invokeNativeObject(17, arrayOf(pointer, WString(title)), WinNT.HRESULT::class.java) as WinNT.HRESULT

        fun setDefaultExtension(extension: String): WinNT.HRESULT =
            _invokeNativeObject(
                22,
                arrayOf(pointer, WString(extension.trimStart('.'))),
                WinNT.HRESULT::class.java,
            ) as WinNT.HRESULT

        fun getResults(): Pointer? {
            val ref = PointerByReference()
            val hr = _invokeNativeObject(27, arrayOf(pointer, ref), WinNT.HRESULT::class.java) as WinNT.HRESULT
            return if (COMUtils.SUCCEEDED(hr)) ref.value else null
        }

        fun getResult(): Pointer? {
            val ref = PointerByReference()
            val hr = _invokeNativeObject(20, arrayOf(pointer, ref), WinNT.HRESULT::class.java) as WinNT.HRESULT
            return if (COMUtils.SUCCEEDED(hr)) ref.value else null
        }
    }

    private class ShellItemArray(pointer: Pointer) : Unknown(pointer) {
        fun paths(): List<String> {
            val countRef = IntByReference()
            val countHr = _invokeNativeObject(7, arrayOf(pointer, countRef), WinNT.HRESULT::class.java) as WinNT.HRESULT
            if (COMUtils.FAILED(countHr)) return emptyList()
            return (0 until countRef.value).mapNotNull { index ->
                val itemRef = PointerByReference()
                val itemHr = _invokeNativeObject(
                    8,
                    arrayOf(pointer, index, itemRef),
                    WinNT.HRESULT::class.java,
                ) as WinNT.HRESULT
                if (COMUtils.FAILED(itemHr)) return@mapNotNull null
                val item = ShellItem(itemRef.value)
                try {
                    item.fileSystemPath()
                } finally {
                    item.Release()
                }
            }
        }
    }

    private class ShellItem(pointer: Pointer) : Unknown(pointer) {
        fun fileSystemPath(): String? {
            val pathRef = PointerByReference()
            val hr = _invokeNativeObject(
                5,
                arrayOf(pointer, SIGDN_FILESYSPATH, pathRef),
                WinNT.HRESULT::class.java
            ) as WinNT.HRESULT
            if (COMUtils.FAILED(hr)) return null
            val pathPointer = pathRef.value ?: return null
            return try {
                pathPointer.getWideString(0)
            } finally {
                Ole32.INSTANCE.CoTaskMemFree(pathPointer)
            }
        }
    }

    @Structure.FieldOrder("pszName", "pszSpec")
    private open class ComDlgFilterSpec : Structure() {
        @JvmField
        var pszName: WString? = null

        @JvmField
        var pszSpec: WString? = null
    }

    private interface Shell32Extra : com.sun.jna.win32.StdCallLibrary {
        fun SHCreateItemFromParsingName(
            pszPath: WString,
            pbc: Pointer?,
            riid: Guid.REFIID,
            ppv: PointerByReference
        ): WinNT.HRESULT

        companion object {
            val INSTANCE: Shell32Extra = Native.load("shell32", Shell32Extra::class.java, W32APIOptions.DEFAULT_OPTIONS)
        }
    }
}
