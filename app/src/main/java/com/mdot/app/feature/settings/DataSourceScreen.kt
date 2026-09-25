package com.mdot.app.feature.settings

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import com.mdot.app.core.designsystem.component.GLASS_AMBIENT_PERIOD_MS
import com.mdot.app.core.designsystem.component.GlassCard
import com.mdot.app.core.designsystem.component.StarBand
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mdot.app.R
import com.mdot.app.core.designsystem.Radius
import com.mdot.app.core.designsystem.IconSpec
import com.mdot.app.core.designsystem.Spacing
import com.mdot.app.core.designsystem.component.SectionCard
import com.mdot.app.core.navigation.contentBottomPadding
import com.mdot.app.core.designsystem.component.JiabanTopBar
import com.mdot.app.core.designsystem.component.SettingRow
import com.mdot.app.core.designsystem.component.MessageSnackbarHost
import com.mdot.app.core.designsystem.component.rememberMessageSnackbar

import kotlin.math.max

/** 更新与数据源（F7-9 / F8-1）：hero 版本卡（检查更新 + from 源选择胶囊，弹窗内选择/添加更新源）+ 节假日库卡，与首页/统计 hero 同视觉体系 */
@Composable
fun DataSourceScreen(
    onBack: () -> Unit,
    vm: DataSourceViewModel = hiltViewModel(),
    updateVm: UpdateViewModel = hiltViewModel(),
) {
    val holidayVersion by vm.holidayVersion.collectAsStateWithLifecycle()
    val holidayUrl by vm.holidayUrl.collectAsStateWithLifecycle()
    val holidayUrls by vm.holidayUrls.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val updateNotice by updateVm.notice.collectAsStateWithLifecycle()
    val updateNoticeIsError by updateVm.noticeIsError.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()

    Box(Modifier.fillMaxSize()) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .contentBottomPadding(showBottomBar = false)
            .padding(horizontal = Spacing.page),
    ) {
        JiabanTopBar(title = stringResource(R.string.datasource_title), onBack = onBack)
        Spacer(Modifier.height(Spacing.m))

        // ---- hero：当前版本 + 检查更新 + from 源选择（与关于页共用，见 UpdateHeroCard） ----
        UpdateHeroCard(updateVm = updateVm, dsVm = vm)

        Spacer(Modifier.height(Spacing.m))

        // ---- 节假日库 ----
        SectionCard {
            Column {
                Text(stringResource(R.string.datasource_section_holiday), style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(Spacing.s))
                SettingRow(
                    stringResource(R.string.datasource_current_lib_version),
                    holidayVersion ?: stringResource(R.string.datasource_builtin_default),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = vm::refreshHoliday,
                        enabled = !busy,
                        shape = RoundedCornerShape(Radius.pill),
                        contentPadding = PaddingValues(horizontal = Spacing.l, vertical = Spacing.s),
                    ) {
                        if (busy) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = LocalContentColor.current,
                            )
                            Spacer(Modifier.width(Spacing.s))
                        }
                        Text(stringResource(R.string.datasource_refresh_now))
                    }
                }
                Text(
                    stringResource(R.string.datasource_builtin_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.xs),
                )
                Spacer(Modifier.height(Spacing.s))
                UrlPicker(
                    urls = holidayUrls,
                    selected = holidayUrl,
                    onSelect = vm::setHolidayUrl,
                    onRemove = vm::removeHolidayUrl,
                    onAdd = vm::addHolidayUrl,
                )
            }
        }

    }
        // 两路消息合并到同一 Snackbar：检查更新提示（updateVm.notice）优先于节假日刷新提示（几乎不会同时出现）
        val snackbarMsg = updateNotice ?: message
        val snackbarIsError = updateNotice != null && updateNoticeIsError
        val (snackbarHostState, snackbarIsErrorFlag) = rememberMessageSnackbar(
            message = snackbarMsg,
            isError = snackbarIsError,
            onClear = { if (updateNotice != null) updateVm.clearNotice() else vm.clearMessage() },
        )
        MessageSnackbarHost(snackbarHostState, snackbarIsErrorFlag, Modifier.align(Alignment.BottomCenter))
    }
}


/**
 * hero 版本卡外观样式。
 *
 * [Classic]（当前启用）= 原 primaryContainer 纯色底 + SectionCard 圆角卡；
 * [Glass]（保留备用）= 渐变玻璃底 + 上边彩虹跑马灯 + 星光带（与致谢卡同款视觉语言）。
 *
 * 两套样式共用同一内容体 [UpdateHeroContent]，切换只影响外壳与标题装饰——
 * 避免复制两份内容代码导致日后漂移（改字段要改两处）。
 * 启用 Glass：把 [UPDATE_HERO_STYLE] 改为 [UpdateHeroStyle.Glass] 即可。
 */
enum class UpdateHeroStyle { Classic, Glass }

/** 当前生效的 hero 卡样式（改这一行即可切换） */
private val UPDATE_HERO_STYLE = UpdateHeroStyle.Classic

/** hero 版本卡：当前版本大字 + 检查更新 + 花体 from 当前源胶囊（点开弹窗选择/添加/删除更新源）。
 *  更新与数据源页与关于页共用；样式见 [UpdateHeroStyle] */
@Composable
internal fun UpdateHeroCard(
    updateVm: UpdateViewModel,
    dsVm: DataSourceViewModel,
) {
    val updateUrl by dsVm.updateUrl.collectAsStateWithLifecycle()
    val updateUrls by dsVm.updateUrls.collectAsStateWithLifecycle()
    val updateState by updateVm.state.collectAsStateWithLifecycle()
    // 已知有新版本未更新 → 版本号后上方星形红点（数据源 = UpdateRepository.check() 落盘的 known）
    val showNewBadge by updateVm.hasKnownUpdate.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val versionText = remember {
        runCatching {
            val pi = context.packageManager.getPackageInfo(context.packageName, 0)
            @Suppress("DEPRECATION")
            "${pi.versionName} (${pi.versionCode})"
        }.getOrDefault("-")
    }
    var showSourcePicker by remember { mutableStateOf(false) }

    when (UPDATE_HERO_STYLE) {
        UpdateHeroStyle.Classic -> SectionCard(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ) {
            UpdateHeroContent(
                versionText = versionText,
                updateUrls = updateUrls,
                updateUrl = updateUrl,
                updateState = updateState,
                showNewBadge = showNewBadge,
                onCheck = updateVm::check,
                onDownload = updateVm::downloadAndInstall,
                onInstall = updateVm::installDownloaded,
                onBackgroundDownload = updateVm::backgroundDownload,
                onPickSource = { showSourcePicker = true },
            )
        }

        UpdateHeroStyle.Glass -> {
            // 玻璃版：星光带相位（周期与致谢卡共用常量）
            val ambient = rememberInfiniteTransition(label = "updateHeroAmbient")
            val starShift by ambient.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = GLASS_AMBIENT_PERIOD_MS, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart,
                ),
                label = "updateHeroStarShift",
            )
            GlassCard {
                UpdateHeroContent(
                    versionText = versionText,
                    updateUrls = updateUrls,
                    updateUrl = updateUrl,
                    updateState = updateState,
                    showNewBadge = showNewBadge,
                    onCheck = updateVm::check,
                    onDownload = updateVm::downloadAndInstall,
                    onInstall = updateVm::installDownloaded,
                    onBackgroundDownload = updateVm::backgroundDownload,
                    onPickSource = { showSourcePicker = true },
                    // 标题装饰：星光带（仅玻璃版提供）
                    titleTrailing = {
                        Spacer(Modifier.width(Spacing.s))
                        StarBand(
                            shift = starShift,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            accent = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.weight(1f),
                        )
                    },
                )
            }
        }
    }

    if (showSourcePicker) {
        UpdateSourcePickerDialog(
            urls = updateUrls,
            selected = updateUrl,
            onSelect = { url ->
                dsVm.setUpdateUrl(url)
                showSourcePicker = false
            },
            onAdd = dsVm::addUpdateUrl,
            onRemove = dsVm::removeUpdateUrl,
            onDismiss = { showSourcePicker = false },
        )
    }
}

/**
 * hero 卡内容体（两套样式共用）：当前版本标签 + 版本大字 + 更新操作按钮 + from 源胶囊。
 * 外观差异通过 [titleTrailing]（标题尾随装饰，仅玻璃版有星光带）注入，其余完全一致。
 * 前景色统一用 onPrimaryContainer——Classic 的 primaryContainer 底与 Glass 的磨砂底都适配。
 *
 * 更新操作按钮为无弹窗状态流转（docs/15 反馈）：检查更新 → 按钮内圆环（检查中）→
 * 有更新变「下载 vX」→ 下载中显示进度（点击转后台）→ 下载完变「安装」→ 装完回「检查更新」。
 * 结果提示（已是最新/失败）走页面级底部 Snackbar，不占卡片布局。
 */
@Composable
private fun UpdateHeroContent(
    versionText: String,
    updateUrls: List<String>,
    updateUrl: String,
    updateState: UpdateState,
    /** 已知有新版本未更新：版本号后上方的星形红点（更新成功或检查到已是最新即消失） */
    showNewBadge: Boolean,
    onCheck: () -> Unit,
    onDownload: () -> Unit,
    onInstall: () -> Unit,
    onBackgroundDownload: () -> Unit,
    onPickSource: () -> Unit,
    titleTrailing: (@Composable androidx.compose.foundation.layout.RowScope.() -> Unit)? = null,
) {
    val onContainer = MaterialTheme.colorScheme.onPrimaryContainer
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.datasource_current_version),
                style = MaterialTheme.typography.labelSmall,
                color = onContainer.copy(alpha = 0.8f),
            )
            titleTrailing?.invoke(this)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                versionText,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = onContainer,
            )
            if (showNewBadge) {
                // 已知有新版本未更新：版本号后上方的星芒红点（致谢卡同款 auto_awesome；
                // 提示动作在下方「检查更新」按钮）
                Icon(
                    painterResource(R.drawable.ic_ms_auto_awesome),
                    contentDescription = stringResource(R.string.update_new_badge_cd),
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .size(IconSpec.inline)
                        .align(Alignment.Top)
                        .padding(top = Spacing.xs),
                )
            }
            if (updateState is UpdateState.BackgroundDownloading) {
                Spacer(Modifier.width(Spacing.m))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(Radius.pill))
                        .padding(horizontal = Spacing.s, vertical = Spacing.xs),
                ) {
                    CircularProgressIndicator(
                        progress = { updateState.progress / 100f },
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(Spacing.xs))
                    Text(
                        stringResource(R.string.update_background_progress, updateState.progress),
                        style = MaterialTheme.typography.labelMedium,
                        color = onContainer,
                    )
                }
            }
        }
        Spacer(Modifier.height(Spacing.s))
        Row(verticalAlignment = Alignment.CenterVertically) {
            UpdateActionButton(
                state = updateState,
                onCheck = onCheck,
                onDownload = onDownload,
                onInstall = onInstall,
                onBackgroundDownload = onBackgroundDownload,
            )
            if (updateUrls.isNotEmpty()) {
                Spacer(Modifier.width(Spacing.s))
                Text(
                    stringResource(R.string.datasource_update_from),
                    style = MaterialTheme.typography.titleSmall,
                    fontFamily = FontFamily.Cursive,
                    color = onContainer.copy(alpha = 0.75f),
                )
                Spacer(Modifier.width(Spacing.xs))
                Button(
                    onClick = onPickSource,
                    shape = RoundedCornerShape(Radius.pill),
                    contentPadding = PaddingValues(horizontal = Spacing.m, vertical = Spacing.xs),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = onContainer.copy(alpha = 0.12f),
                        contentColor = onContainer,
                    ),
                ) {
                    Text(
                        sourceDisplayName(updateUrl),
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/**
 * 更新操作按钮：无弹窗状态流转（docs/15 反馈）。
 * - Checking/Downloading/BackgroundDownloading 时按钮承载圆环/进度且视觉保持 primary 主色
 *   （禁用仅拦截点击，不置灰）；Downloading 点击转后台下载（进度徽章同步出现在版本行右侧）。
 * - 宽度随内容紧凑排布（空闲态无大空白）；busy 态只留圆环居中、移除文字；
 *   收缩动画由单一 Animatable 驱动宽度+位置 → 从两侧往中间对称缩小。
 */
@Composable
private fun UpdateActionButton(
    state: UpdateState,
    onCheck: () -> Unit,
    onDownload: () -> Unit,
    onInstall: () -> Unit,
    onBackgroundDownload: () -> Unit,
) {
    val shape = RoundedCornerShape(Radius.pill)
    // 进行中状态视觉不变（primary 底 + onPrimary 内容），enabled=false 只拦截点击
    val activeColors = ButtonDefaults.buttonColors(
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        disabledContainerColor = MaterialTheme.colorScheme.primary,
        disabledContentColor = MaterialTheme.colorScheme.onPrimary,
    )

    // ---- 状态 → 触发行为 + 渲染数据（单点分发，避免多分支漂移） ----
    val busy = state is UpdateState.Checking ||
        state is UpdateState.Downloading ||
        state is UpdateState.BackgroundDownloading
    val contentPadding = PaddingValues(horizontal = Spacing.l, vertical = Spacing.s)
    val progress: Float? = when (state) {
        is UpdateState.Downloading -> state.progress / 100f
        is UpdateState.BackgroundDownloading -> state.progress / 100f
        else -> null
    }
    val label: String = when (state) {
        UpdateState.Idle -> stringResource(R.string.datasource_check_update)
        UpdateState.Checking -> stringResource(R.string.update_checking_short)
        is UpdateState.Available -> stringResource(R.string.update_action_download_version, state.info.versionName)
        is UpdateState.Downloading -> stringResource(R.string.update_background_progress, state.progress)
        is UpdateState.BackgroundDownloading -> stringResource(R.string.update_background_progress, state.progress)
        is UpdateState.Downloaded -> stringResource(R.string.update_action_install_short)
    }
    val enabled = state is UpdateState.Idle ||
        state is UpdateState.Available ||
        state is UpdateState.Downloading ||
        state is UpdateState.Downloaded
    val onClick: () -> Unit = when (state) {
        UpdateState.Idle -> onCheck
        is UpdateState.Available -> onDownload
        is UpdateState.Downloading -> onBackgroundDownload
        is UpdateState.Downloaded -> onInstall
        else -> ({ })
    }

    // 收缩/展开动画：宽度+位置由同一 Animatable 驱动 → 每帧左右对称、无右侧缺失。
    // ⚠️ 背景：animateContentSize + Box(contentAlignment=Center) 时，尺寸动画与位置动画
    //    不同步（录屏实测：尺寸瞬间缩到 159px、位置随后 0.4s 慢速漂移）→ 右侧瞬时空洞
    //    + 两边收缩速度不同。改用单一 shrink 进度：按钮宽 = lerp(idleW, busyW, shrink)，
    //    容器固定 idleW + contentAlignment=Center → 位置 = (idleW - 宽)/2 随 shrink 同步，
    //    左缘 = (Δ/2)·t、右缘 = idleW - (Δ/2)·t，完全对称。
    // 展开（busy→idle）时文字（≈238px）在插值宽 < 文字宽时会被压缩省略——文字用 alpha
    // 渐入（宽 > 230px 才可见），视觉为「按钮扩宽 + 文字淡入」，无省略号闪动。
    // 收缩动画 spec（fastSpatialSpec 是 @Composable 泛型函数，须显式 <Float> 且在 Composable 上下文先取值）
    val shrinkSpec = MaterialTheme.motionScheme.fastSpatialSpec<Float>()
    val shrink = remember { Animatable(0f) }
    val compact = busy || state is UpdateState.Available
    LaunchedEffect(compact) {
        shrink.animateTo(if (compact) 1f else 0f, shrinkSpec)
    }
    var idleWpx by remember { mutableStateOf(0) }
    val density = LocalDensity.current
    // busy 目标宽：环 14 + 水平 padding 2×Spacing.l；M3E Button minWidth 60dp 兜底
    val busyWdp = Spacing.l * 2 + 14.dp
    val busyWpx = with(density) { (if (busyWdp > 60.dp) busyWdp else 60.dp).toPx() }.toInt()
    val interpWpx = if (idleWpx > 0) {
        (idleWpx + (busyWpx - idleWpx) * shrink.value)
    } else {
        null
    }
    val textAlpha = if (interpWpx != null) {
        ((interpWpx - 230f) / 8f).coerceIn(0f, 1f)
    } else {
        1f
    }

    Box(
        modifier = if (idleWpx > 0) {
            Modifier.width(with(density) { idleWpx.toDp() })
        } else {
            Modifier
        },
        contentAlignment = Alignment.Center,
    ) {
        Button(
            onClick = onClick,
            enabled = enabled,
            shape = shape,
            contentPadding = contentPadding,
            colors = if (busy) activeColors else ButtonDefaults.buttonColors(),
            modifier = Modifier
                .then(
                    if (interpWpx != null) {
                        Modifier.width(with(density) { interpWpx.toDp() })
                    } else {
                        Modifier
                    },
                )
                .onGloballyPositioned {
                    // 记录 idle 自然宽（idle 时按钮宽 = interp = idleW，稳定不循环）
                    if (!compact && it.size.width > idleWpx) idleWpx = it.size.width
                },
        ) {
            // busy 态只显示圆环（Button 内容默认居中），无文字——用户明确要求；
            // 非 busy 态显示状态文字；文字直接切换（不用 Crossfade——新旧文字交叉淡化
            // 的瞬间观感是「闪」，用户已否）。
            ButtonSpinner(busy = busy, progress = progress)
            if (state is UpdateState.Available) {
                Icon(
                    painterResource(R.drawable.ic_ms_file_download),
                    contentDescription = label,
                    modifier = Modifier.size(ButtonDefaults.IconSize),
                )
            } else if (!busy) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.alpha(textAlpha),
                )
            }
        }
    }
}

/**
 * 按钮内圆环：busy 时淡入淡出 + 横向展开（progress 非空=定环，null=不定环），居中于按钮。
 * color 必须传 onPrimary：CircularProgressIndicator 默认 primary 色与按钮底色同色不可见。
 */
@Composable
private fun ButtonSpinner(
    busy: Boolean,
    progress: Float?,
) {
    AnimatedVisibility(
        visible = busy,
        enter = fadeIn(MaterialTheme.motionScheme.fastEffectsSpec()) +
            expandHorizontally(animationSpec = MaterialTheme.motionScheme.fastSpatialSpec()),
        exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()) +
            shrinkHorizontally(animationSpec = MaterialTheme.motionScheme.fastSpatialSpec()),
    ) {
        val p = progress
        if (p != null) {
            CircularProgressIndicator(
                progress = { p },
                modifier = Modifier.size(14.dp),
                strokeWidth = 1.5.dp,
                color = MaterialTheme.colorScheme.onPrimary,
                trackColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.3f),
            )
        } else {
            CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                strokeWidth = 1.5.dp,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}
/** URL 多选一列表：RadioButton 点选即生效；多项时可删除；底部添加（对话框校验 http(s) 前缀与重复） */
@Composable
private fun UrlPicker(
    urls: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    onRemove: (String) -> Unit,
    onAdd: (String) -> Unit,
) {
    var showAdd by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        // 候选列表限高（约 3 行），多地址时内部滚动避免整页拉长
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 190.dp)
                .verticalScroll(rememberScrollState())
        ) {
        urls.forEach { url ->
            val isSel = url == selected
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radius.small))
                    .background(
                        if (isSel) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                        else Color.Transparent
                    )
                    .clickable(onClick = { onSelect(url) })
                    .padding(horizontal = Spacing.s, vertical = Spacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = isSel, onClick = { onSelect(url) })
                Text(
                    url,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (urls.size > 1) {
                    IconButton(onClick = { onRemove(url) }) {
                        Icon(
                            painterResource(R.drawable.ic_ms_delete),
                            contentDescription = stringResource(R.string.ds_confirm_delete),
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
        }
        Spacer(Modifier.height(Spacing.s))
        OutlinedButton(
            onClick = { showAdd = true },
            shape = RoundedCornerShape(Radius.pill),
            contentPadding = PaddingValues(horizontal = Spacing.l, vertical = Spacing.s),
        ) {
            Icon(painterResource(R.drawable.ic_ms_add), null, Modifier.size(IconSpec.inline))
            Spacer(Modifier.width(Spacing.s))
            Text(stringResource(R.string.datasource_add_url), style = MaterialTheme.typography.labelMedium)
        }
    }
    if (showAdd) {
        var text by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text(stringResource(R.string.datasource_add_url)) },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(stringResource(R.string.datasource_add_url_hint)) },
                    singleLine = true,
                    shape = RoundedCornerShape(Radius.textField),
                )
            },
            confirmButton = {
                TextButton(
                    enabled = text.isNotBlank(),
                    onClick = {
                        onAdd(text)
                        showAdd = false
                    },
                ) { Text(stringResource(R.string.datasource_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showAdd = false }) { Text(stringResource(R.string.datasource_cancel)) }
            },
        )
    }
}

/** 更新数据源选择弹窗：名称 + 地址点选即生效，行内可删（多项时）、底部可添加新地址（更新源唯一管理入口） */
@Composable
private fun UpdateSourcePickerDialog(
    urls: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var showAdd by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.datasource_source_picker_title)) },
        text = {
            Column(
                Modifier
                    .heightIn(max = 320.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                urls.forEach { url ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(Radius.small))
                            .clickable(onClick = { onSelect(url) })
                            .padding(horizontal = Spacing.xs, vertical = Spacing.s),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = url == selected, onClick = null)
                        Column(Modifier.weight(1f).padding(start = Spacing.s)) {
                            Text(
                                sourceDisplayName(url),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                url,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (urls.size > 1) {
                            IconButton(onClick = { onRemove(url) }) {
                                Icon(
                                    painterResource(R.drawable.ic_ms_delete),
                                    contentDescription = stringResource(R.string.ds_confirm_delete),
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(Spacing.xs))
                OutlinedButton(
                    onClick = { showAdd = true },
                    shape = RoundedCornerShape(Radius.pill),
                    contentPadding = PaddingValues(horizontal = Spacing.l, vertical = Spacing.s),
                ) {
                    Icon(painterResource(R.drawable.ic_ms_add), null, Modifier.size(IconSpec.inline))
                    Spacer(Modifier.width(Spacing.s))
                    Text(stringResource(R.string.datasource_add_url), style = MaterialTheme.typography.labelMedium)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.datasource_cancel)) }
        },
    )
    if (showAdd) {
        var text by remember { mutableStateOf("") }
        val t = text.trim()
        val dup = t.isNotEmpty() && t in urls
        AlertDialog(
            onDismissRequest = { showAdd = false },
            title = { Text(stringResource(R.string.datasource_add_url)) },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(stringResource(R.string.datasource_add_url_hint)) },
                    singleLine = true,
                    shape = RoundedCornerShape(Radius.textField),
                    isError = dup,
                    supportingText = if (dup) {
                        { Text(stringResource(R.string.datasource_add_url_dup)) }
                    } else null,
                )
            },
            confirmButton = {
                TextButton(
                    enabled = (t.startsWith("http://") || t.startsWith("https://")) && !dup,
                    onClick = {
                        onAdd(t)
                        showAdd = false
                    },
                ) { Text(stringResource(R.string.datasource_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showAdd = false }) { Text(stringResource(R.string.datasource_cancel)) }
            },
        )
    }
}

/** 数据源展示名：知名域名用品牌名（专有名词非文案，不入 strings.xml），自定义地址取域名，供胶囊按钮与弹窗显示 */
private fun sourceDisplayName(url: String): String = when {
    url.contains("cnb.cool") -> "CNB"
    url.contains("github.io") || url.contains("github.com") || url.contains("raw.githubusercontent.com") -> "GitHub"
    else -> Uri.parse(url).host ?: url
}
