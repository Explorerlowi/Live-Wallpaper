package com.example.livewallpaper.paint.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.livewallpaper.R
import com.example.livewallpaper.feature.aipaint.domain.model.*
import com.example.livewallpaper.ui.components.ConfirmDialog
import com.example.livewallpaper.ui.components.SelectOption
import com.example.livewallpaper.ui.components.SimpleSelectDialog
import java.text.SimpleDateFormat
import java.util.*
import kotlin.random.Random

/**
 * 会话时间分组类型
 */
private enum class SessionTimeGroup {
    TODAY,
    LAST_7_DAYS,
    LAST_30_DAYS,
    OLDER  // 按年月分组
}

/**
 * 分组后的会话数据
 */
private data class GroupedSessions(
    val label: String,
    val sessions: List<PaintSession>
)

/**
 * 会话抽屉内容组件（用于 ModalNavigationDrawer）
 */
@Composable
fun SessionDrawerContent(
    sessions: List<PaintSession>,
    currentSessionId: String?,
    generatingSessionCounts: Map<String, Int> = emptyMap(),
    onSelectSession: (String) -> Unit,
    onCreateSession: () -> Unit,
    onDeleteSession: (String) -> Unit,
    onRenameSession: (String, String) -> Unit = { _, _ -> }
) {
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val drawerWidth = screenWidth * 0.82f
    
    // 删除确认对话框状态
    var sessionToDelete by remember { mutableStateOf<PaintSession?>(null) }
    
    // 重命名对话框状态
    var sessionToRename by remember { mutableStateOf<PaintSession?>(null) }
    
    // 时间分组标签
    val todayLabel = stringResource(R.string.session_group_today)
    val last7DaysLabel = stringResource(R.string.session_group_7_days)
    val last30DaysLabel = stringResource(R.string.session_group_30_days)
    
    // 按时间分组会话
    val groupedSessions = remember(sessions) {
        groupSessionsByTime(sessions, todayLabel, last7DaysLabel, last30DaysLabel)
    }
    
    ModalDrawerSheet(
        modifier = Modifier.width(drawerWidth),
        drawerContainerColor = MaterialTheme.colorScheme.surface,
        drawerShape = RoundedCornerShape(0.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // 状态栏占位
            Spacer(modifier = Modifier.statusBarsPadding())
            
            // 标题栏 - 高度与TopAppBar对齐（64dp）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.paint_sessions),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .clickable(
                            onClick = onCreateSession,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = stringResource(R.string.paint_new_session),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )
            
            // 会话列表
            if (sessions.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Outlined.ChatBubbleOutline,
                                contentDescription = null,
                                modifier = Modifier.size(36.dp),
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.paint_no_sessions),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.paint_no_sessions_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        )
                    }
                }
            } else {
                val navigationBarPadding = WindowInsets.navigationBars.asPaddingValues()
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 12.dp,
                        end = 12.dp,
                        top = 8.dp,
                        bottom = 8.dp + navigationBarPadding.calculateBottomPadding()
                    ),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    groupedSessions.forEach { group ->
                        // 分组标题
                        item(key = "header_${group.label}") {
                            SessionGroupHeader(label = group.label)
                        }
                        // 分组内的会话
                        items(group.sessions, key = { it.id }) { session ->
                            SessionItem(
                                session = session,
                                isSelected = session.id == currentSessionId,
                                generatingCount = generatingSessionCounts[session.id] ?: 0,
                                onClick = { onSelectSession(session.id) },
                                onRename = { sessionToRename = session },
                                onDelete = { sessionToDelete = session }
                            )
                        }
                    }
                }
            }
        }
    }
    
    // 删除确认对话框
    sessionToDelete?.let { session ->
        ConfirmDialog(
            title = stringResource(R.string.paint_delete_session_title),
            message = stringResource(R.string.paint_delete_session_message, session.title),
            confirmText = stringResource(R.string.paint_delete),
            dismissText = stringResource(R.string.cancel),
            isDangerous = true,
            onConfirm = { onDeleteSession(session.id) },
            onDismiss = { sessionToDelete = null }
        )
    }
    
    // 重命名对话框
    sessionToRename?.let { session ->
        com.example.livewallpaper.ui.components.TextInputDialog(
            title = stringResource(R.string.paint_rename_session),
            initialValue = session.title,
            label = stringResource(R.string.paint_session_name_label),
            description = stringResource(R.string.paint_session_name_hint),
            singleLine = true,
            confirmText = stringResource(R.string.confirm),
            dismissText = stringResource(R.string.cancel),
            maxLength = 50,
            onConfirm = { newTitle ->
                onRenameSession(session.id, newTitle)
            },
            onDismiss = { sessionToRename = null }
        )
    }
}

/**
 * 按时间分组会话
 */
private fun groupSessionsByTime(
    sessions: List<PaintSession>,
    todayLabel: String,
    last7DaysLabel: String,
    last30DaysLabel: String
): List<GroupedSessions> {
    if (sessions.isEmpty()) return emptyList()
    
    val now = System.currentTimeMillis()
    val calendar = Calendar.getInstance()
    
    // 计算今天开始时间
    calendar.timeInMillis = now
    calendar.set(Calendar.HOUR_OF_DAY, 0)
    calendar.set(Calendar.MINUTE, 0)
    calendar.set(Calendar.SECOND, 0)
    calendar.set(Calendar.MILLISECOND, 0)
    val todayStart = calendar.timeInMillis
    
    // 计算7天前开始时间
    calendar.add(Calendar.DAY_OF_YEAR, -7)
    val sevenDaysAgo = calendar.timeInMillis
    
    // 计算30天前开始时间
    calendar.timeInMillis = todayStart
    calendar.add(Calendar.DAY_OF_YEAR, -30)
    val thirtyDaysAgo = calendar.timeInMillis
    
    // 年月格式化
    val yearMonthFormat = SimpleDateFormat("yyyy年M月", Locale.getDefault())
    
    // 按更新时间降序排序
    val sortedSessions = sessions.sortedByDescending { it.updatedAt }
    
    // 分组
    val todaySessions = mutableListOf<PaintSession>()
    val last7DaysSessions = mutableListOf<PaintSession>()
    val last30DaysSessions = mutableListOf<PaintSession>()
    val olderSessionsMap = mutableMapOf<String, MutableList<PaintSession>>()
    
    sortedSessions.forEach { session ->
        when {
            session.updatedAt >= todayStart -> todaySessions.add(session)
            session.updatedAt >= sevenDaysAgo -> last7DaysSessions.add(session)
            session.updatedAt >= thirtyDaysAgo -> last30DaysSessions.add(session)
            else -> {
                val yearMonth = yearMonthFormat.format(Date(session.updatedAt))
                olderSessionsMap.getOrPut(yearMonth) { mutableListOf() }.add(session)
            }
        }
    }
    
    // 构建结果列表
    val result = mutableListOf<GroupedSessions>()
    
    if (todaySessions.isNotEmpty()) {
        result.add(GroupedSessions(todayLabel, todaySessions))
    }
    if (last7DaysSessions.isNotEmpty()) {
        result.add(GroupedSessions(last7DaysLabel, last7DaysSessions))
    }
    if (last30DaysSessions.isNotEmpty()) {
        result.add(GroupedSessions(last30DaysLabel, last30DaysSessions))
    }
    
    // 按年月降序添加更早的会话
    olderSessionsMap.entries
        .sortedByDescending { it.value.firstOrNull()?.updatedAt ?: 0L }
        .forEach { (yearMonth, sessionList) ->
            result.add(GroupedSessions(yearMonth, sessionList))
        }
    
    return result
}

/**
 * 分组标题组件
 */
@Composable
private fun SessionGroupHeader(label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 8.dp)
    )
}

@Composable
private fun SessionItem(
    session: PaintSession,
    isSelected: Boolean,
    generatingCount: Int = 0,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("MM/dd HH:mm", Locale.getDefault()) }
    
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.padding(vertical = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // 第一行：标题 + 删除按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 标题文字，只占实际宽度，点击触发重命名
                Text(
                    text = session.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f, fill = false)  // 不填充剩余空间
                        .pointerInput(Unit) {
                            detectTapGestures { onRename() }
                        }
                )
                // 占位，点击这里会触发父级的 onClick（打开会话）
                Spacer(modifier = Modifier.weight(1f))
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.DeleteOutline,
                        contentDescription = stringResource(R.string.paint_delete),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(6.dp))
            
            // 第二行：生成状态 / 时间
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (generatingCount > 0) {
                    // 生成中状态
                    CircularProgressIndicator(
                        modifier = Modifier.size(12.dp),
                        strokeWidth = 1.5.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = stringResource(R.string.paint_session_generating, generatingCount),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                
                Spacer(modifier = Modifier.weight(1f))
                
                // 时间
                Text(
                    text = dateFormat.format(Date(session.updatedAt)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                )
            }
        }
    }
}

@Composable
fun ApiSettingsDialog(
    profiles: List<ApiProfile>,
    activeProfileId: String?,
    onSaveProfile: (ApiProfile) -> Unit,
    onDeleteProfile: (String) -> Unit,
    onSetActive: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var editingProfile by remember { mutableStateOf<ApiProfile?>(null) }
    var showForm by remember { mutableStateOf(false) }
    
    // 删除确认对话框状态
    var profileToDelete by remember { mutableStateOf<ApiProfile?>(null) }
    
    // 表单状态
    var name by remember { mutableStateOf("") }
    var baseUrl by remember { mutableStateOf("https://yunwu.ai") }
    var token by remember { mutableStateOf("") }
    var authMode by remember { mutableStateOf(AuthMode.BEARER) }
    var showToken by remember { mutableStateOf(false) }

    fun resetForm() {
        editingProfile = null
        name = ""
        baseUrl = "https://yunwu.ai"
        token = ""
        authMode = AuthMode.BEARER
        showForm = false
    }

    fun loadProfile(profile: ApiProfile) {
        editingProfile = profile
        name = profile.name
        baseUrl = profile.baseUrl
        token = profile.token
        authMode = profile.authMode
        showForm = true
    }

    Dialog(
        onDismissRequest = onDismiss
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.8f),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // 标题栏
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.paint_api_settings),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Row {
                        if (showForm) {
                            TextButton(onClick = { resetForm() }) {
                                Text(stringResource(R.string.cancel))
                            }
                        }
                        IconButton(onClick = { 
                            if (showForm) resetForm() else showForm = true 
                        }) {
                            Icon(
                                if (showForm) Icons.Default.Close else Icons.Default.Add,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
                
                HorizontalDivider()

                if (showForm) {
                    // 编辑表单
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp, vertical = 18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text(stringResource(R.string.paint_config_name)) },
                            placeholder = { Text(stringResource(R.string.paint_config_name_hint)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        
                        OutlinedTextField(
                            value = baseUrl,
                            onValueChange = { baseUrl = it },
                            label = { Text(stringResource(R.string.paint_api_base_url)) },
                            placeholder = { Text(stringResource(R.string.paint_api_base_url_hint)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        
                        OutlinedTextField(
                            value = token,
                            onValueChange = { token = it },
                            label = { Text(stringResource(R.string.paint_access_token)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { showToken = !showToken }) {
                                    Icon(
                                        if (showToken) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = null
                                    )
                                }
                            }
                        )
                        
                        Text(
                            text = stringResource(R.string.paint_auth_mode),
                            style = MaterialTheme.typography.labelLarge
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            FilterChip(
                                selected = authMode == AuthMode.BEARER,
                                onClick = { authMode = AuthMode.BEARER },
                                label = { Text(stringResource(R.string.paint_auth_bearer_token)) },
                                modifier = Modifier.weight(1f)
                            )
                            FilterChip(
                                selected = authMode == AuthMode.OFFICIAL,
                                onClick = { authMode = AuthMode.OFFICIAL },
                                label = { Text(stringResource(R.string.paint_auth_official)) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        val defaultProfileName =
                            stringResource(R.string.paint_config_default_name, profiles.size + 1)
                        
                        Button(
                            onClick = {
                                val profile = ApiProfile(
                                    id = editingProfile?.id ?: "${System.currentTimeMillis()}-${Random.nextInt(1000)}",
                                    name = name.ifEmpty { defaultProfileName },
                                    baseUrl = baseUrl.trimEnd('/'),
                                    token = token,
                                    authMode = authMode
                                )
                                onSaveProfile(profile)
                                resetForm()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = token.isNotEmpty()
                        ) {
                            Text(if (editingProfile != null) stringResource(R.string.paint_update) else stringResource(R.string.paint_save))
                        }
                    }
                } else {
                    // 配置列表
                    if (profiles.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Default.Key,
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = stringResource(R.string.paint_no_config),
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                TextButton(onClick = { showForm = true }) {
                                    Text(stringResource(R.string.paint_add_config))
                                }
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(profiles, key = { it.id }) { profile ->
                                ApiProfileItem(
                                    profile = profile,
                                    isActive = profile.id == activeProfileId,
                                    onClick = { onSetActive(profile.id) },
                                    onEdit = { loadProfile(profile) },
                                    onDelete = { profileToDelete = profile }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    
    // 删除确认对话框
    profileToDelete?.let { profile ->
        ConfirmDialog(
            title = stringResource(R.string.paint_delete_config_title),
            message = stringResource(R.string.paint_delete_config_message, profile.name),
            confirmText = stringResource(R.string.paint_delete),
            dismissText = stringResource(R.string.cancel),
            isDangerous = true,
            onConfirm = { onDeleteProfile(profile.id) },
            onDismiss = { profileToDelete = null }
        )
    }
}

@Composable
private fun ApiProfileItem(
    profile: ApiProfile,
    isActive: Boolean,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = if (isActive) androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = profile.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (isActive) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
            Row {
                IconButton(onClick = onEdit) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = stringResource(R.string.paint_edit),
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = stringResource(R.string.paint_delete),
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ModelSelectorDialog(
    selectedModel: PaintModel,
    onSelect: (PaintModel) -> Unit,
    onDismiss: () -> Unit
) {
    val options = PaintModel.entries.map { model ->
        SelectOption(
            value = model,
            label = model.displayName,
            icon = Icons.Default.AutoAwesome
        )
    }
    
    SimpleSelectDialog(
        options = options,
        selectedValue = selectedModel,
        onSelect = onSelect,
        onDismiss = onDismiss
    )
}

@Composable
fun RatioSelectorDialog(
    selectedRatio: AspectRatio,
    selectedModel: PaintModel = PaintModel.GEMINI_2_5_FLASH,
    onSelect: (AspectRatio) -> Unit,
    onDismiss: () -> Unit
) {
    val availableRatios = AspectRatio.availableFor(selectedModel)
    val options = availableRatios.map { ratio ->
        // 解析比例值
        val parts = ratio.value.split(":")
        val widthRatio = parts[0].toIntOrNull() ?: 1
        val heightRatio = parts[1].toIntOrNull() ?: 1
        
        SelectOption(
            value = ratio,
            label = ratio.displayName,
            iconContent = {
                com.example.livewallpaper.ui.components.RatioIcon(
                    widthRatio = widthRatio,
                    heightRatio = heightRatio
                )
            }
        )
    }
    
    SimpleSelectDialog(
        options = options,
        selectedValue = selectedRatio,
        onSelect = onSelect,
        onDismiss = onDismiss
    )
}

@Composable
fun ResolutionSelectorDialog(
    selectedResolution: Resolution,
    selectedModel: PaintModel = PaintModel.GEMINI_3_PRO,
    onSelect: (Resolution) -> Unit,
    onDismiss: () -> Unit
) {
    val availableResolutions = Resolution.availableFor(selectedModel)
    val options = availableResolutions.map { resolution ->
        SelectOption(
            value = resolution,
            label = resolution.displayName,
            icon = Icons.Default.HighQuality
        )
    }
    
    SimpleSelectDialog(
        options = options,
        selectedValue = selectedResolution,
        onSelect = onSelect,
        onDismiss = onDismiss
    )
}

/**
 * GPT 图片尺寸选择器
 * 使用 RatioIcon 显示每个尺寸对应的形状
 */
@Composable
fun GptSizeSelectorDialog(
    selectedSize: GptImageSize,
    onSelect: (GptImageSize) -> Unit,
    onDismiss: () -> Unit
) {
    val autoLabel = stringResource(R.string.paint_gpt_size_auto)
    val options = GptImageSize.entries.map { size ->
        // 从尺寸值中解析宽高比例
        val parts = size.value.split("x")
        val widthRatio = parts.getOrNull(0)?.toIntOrNull()
        val heightRatio = parts.getOrNull(1)?.toIntOrNull()

        if (widthRatio != null && heightRatio != null) {
            // 简化比例用于图标显示（如 1536:1024 → 3:2）
            val gcd = gcd(widthRatio, heightRatio)
            val simpleW = widthRatio / gcd
            val simpleH = heightRatio / gcd
            SelectOption(
                value = size,
                label = size.displayName,
                iconContent = {
                    com.example.livewallpaper.ui.components.RatioIcon(
                        widthRatio = simpleW,
                        heightRatio = simpleH
                    )
                }
            )
        } else {
            // "auto" 没有具体尺寸，用默认图标
            SelectOption(
                value = size,
                label = autoLabel,
                icon = Icons.Default.AutoAwesome
            )
        }
    }

    SimpleSelectDialog(
        options = options,
        selectedValue = selectedSize,
        onSelect = onSelect,
        onDismiss = onDismiss
    )
}

/** 求最大公约数 */
private fun gcd(a: Int, b: Int): Int = if (b == 0) a else gcd(b, a % b)

/**
 * GPT 图片质量选择器
 */
@Composable
fun GptQualitySelectorDialog(
    selectedQuality: GptImageQuality,
    onSelect: (GptImageQuality) -> Unit,
    onDismiss: () -> Unit
) {
    val options = GptImageQuality.entries.map { quality ->
        SelectOption(
            value = quality,
            label = when (quality) {
                GptImageQuality.AUTO -> stringResource(R.string.paint_gpt_quality_auto)
                GptImageQuality.LOW -> stringResource(R.string.paint_gpt_quality_low)
                GptImageQuality.MEDIUM -> stringResource(R.string.paint_gpt_quality_medium)
                GptImageQuality.HIGH -> stringResource(R.string.paint_gpt_quality_high)
            },
            icon = Icons.Default.HighQuality
        )
    }

    SimpleSelectDialog(
        options = options,
        selectedValue = selectedQuality,
        onSelect = onSelect,
        onDismiss = onDismiss
    )
}

/**
 * GPT 输出格式选择器
 */
@Composable
fun GptFormatSelectorDialog(
    selectedFormat: GptOutputFormat,
    onSelect: (GptOutputFormat) -> Unit,
    onDismiss: () -> Unit
) {
    val options = GptOutputFormat.entries.map { format ->
        SelectOption(
            value = format,
            label = when (format) {
                GptOutputFormat.PNG -> stringResource(R.string.paint_gpt_format_png)
                GptOutputFormat.JPEG -> stringResource(R.string.paint_gpt_format_jpeg)
                GptOutputFormat.WEBP -> stringResource(R.string.paint_gpt_format_webp)
            },
            icon = Icons.Default.Image
        )
    }

    SimpleSelectDialog(
        options = options,
        selectedValue = selectedFormat,
        onSelect = onSelect,
        onDismiss = onDismiss
    )
}
