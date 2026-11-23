package com.mdot.app.feature.settings

import com.mdot.app.core.designsystem.component.JiabanAlertDialog
import com.mdot.app.core.designsystem.dialogContainerColor
import com.mdot.app.core.designsystem.engineShape
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mdot.app.R
import com.mdot.app.core.designsystem.Radius
import com.mdot.app.core.designsystem.IconSpec
import com.mdot.app.core.designsystem.Spacing
import com.mdot.app.core.designsystem.component.SectionCard

import kotlin.math.max
import com.mdot.app.core.designsystem.component.JiabanButton
import com.mdot.app.core.designsystem.component.JiabanButtonRole
import com.mdot.app.core.designsystem.component.JiabanRadioButton
import com.mdot.app.core.designsystem.component.JiabanCircularProgress
import com.mdot.app.core.designsystem.component.FloatingLabelTextField
import top.yukonga.miuix.kmp.basic.Button as MiuixButton
import top.yukonga.miuix.kmp.basic.ButtonColors as MiuixButtonColors
import com.mdot.app.core.designsystem.emphasisCardSurface
import com.mdot.app.core.designsystem.emphasisCardInk
import com.mdot.app.core.designsystem.secondaryCardSurface
import com.mdot.app.core.designsystem.secondaryCardInk
import com.mdot.app.domain.model.ThemeEngine

/**
 * 节假日库卡：版式对齐 [UpdateHeroCard]，卡底与文字用低一档的 [secondaryCardSurface]。
 * 地址管理收进「from 源」弹窗，不再在页面里铺整列 URL。
 */
@Composable
internal fun HolidayCard(vm: DataSourceViewModel) {
    val version by vm.holidayVersion.collectAsStateWithLifecycle()
    val url by vm.holidayUrl.collectAsStateWithLifecycle()
    val urls by vm.holidayUrls.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val ink = secondaryCardInk()
    var showPicker by remember { mutableStateOf(false) }

    SectionCard(containerColor = secondaryCardSurface()) {
        Column(Modifier.fillMaxWidth()) {
            Text(
                stringResource(R.string.datasource_section_holiday),
                style = MaterialTheme.typography.labelSmall,
                color = ink.copy(alpha = 0.8f),
            )
            Spacer(Modifier.height(Spacing.xs))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    version ?: stringResource(R.string.datasource_builtin_default),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = ink,
                )
                if (busy) {
                    Spacer(Modifier.width(Spacing.m))
                    JiabanCircularProgress(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = ink,
                    )
                }
            }
            Spacer(Modifier.height(Spacing.s))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (Radius.engine == ThemeEngine.MIUIX) {
                    // MIUIX 下与版本卡「检查更新」同源（库 Button 的 squircle 底色），
                    // 不套外层 border——边框用药丸圆角会与库按钮的圆角对不上。
                    MiuixButton(
                        onClick = vm::refreshHoliday,
                        enabled = !busy,
                        colors = MiuixButtonColors(
                            color = ink.copy(alpha = 0.10f),
                            disabledColor = ink.copy(alpha = 0.06f),
                            contentColor = ink,
                            disabledContentColor = ink.copy(alpha = 0.38f),
                        ),
                    ) {
                        Text(
                            stringResource(R.string.datasource_refresh_now),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                } else {
                    JiabanButton(
                        text = stringResource(R.string.datasource_refresh_now),
                        onClick = vm::refreshHoliday,
                        role = JiabanButtonRole.SECONDARY,
                        enabled = !busy,
                        contentColorOverride = ink,
                    )
                }
                if (urls.isNotEmpty()) {
                    Spacer(Modifier.width(Spacing.s))
                    Text(
                        stringResource(R.string.datasource_update_from),
                        style = MaterialTheme.typography.titleSmall,
                        fontFamily = FontFamily.Cursive,
                        color = ink.copy(alpha = 0.75f),
                    )
                    Spacer(Modifier.width(Spacing.xs))
                    Button(
                        onClick = { showPicker = true },
                        shape = engineShape(Radius.pill),
                        contentPadding = PaddingValues(horizontal = Spacing.m, vertical = Spacing.xs),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ink.copy(alpha = 0.12f),
                            contentColor = ink,
                        ),
                    ) {
                        Text(
                            sourceDisplayName(url),
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }

    if (showPicker) {
        SourcePickerDialog(
            title = stringResource(R.string.datasource_holiday_source_picker_title),
            urls = urls,
            selected = url,
            onSelect = { picked ->
                vm.setHolidayUrl(picked)
                showPicker = false
            },
            onAdd = vm::addHolidayUrl,
            onRemove = vm::removeHolidayUrl,
            onDismiss = { showPicker = false },
        )
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

/** hero 版本卡（关于页）：当前版本大字 + 检查更新 + 花体 from 当前源胶囊（点开弹窗选择/添加/删除更新源）。样式见 [UpdateHeroStyle] */
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
            containerColor = emphasisCardSurface(),
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
                            tint = emphasisCardInk(),
                            accent = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.weight(1f),
                        )
                    },
                )
            }
        }
    }

    if (showSourcePicker) {
        SourcePickerDialog(
            title = stringResource(R.string.datasource_source_picker_title),
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
    val onContainer = emphasisCardInk()
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
                        .clip(engineShape(Radius.pill))
                        .padding(horizontal = Spacing.s, vertical = Spacing.xs),
                ) {
                    JiabanCircularProgress(
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
                    shape = engineShape(Radius.pill),
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
    val shape = engineShape(Radius.pill)
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
    // 按钮 modifier 链：收缩动画宽度 + idle 自然宽测量（两引擎共用）
    val buttonModifier = Modifier
        .then(
            if (interpWpx != null) {
                Modifier.width(with(density) { interpWpx.toDp() })
            } else {
                Modifier
            }
        )
        .onGloballyPositioned {
            if (!compact && it.size.width > idleWpx) idleWpx = it.size.width
        }

    if (Radius.engine == ThemeEngine.MIUIX) {
        // MIUIX：走库 Button 浅灰底胶囊（与同屏"GitHub"数据源胶囊同款 Miuix 中性按钮）
        MiuixButton(
            onClick = onClick,
            enabled = enabled,
            modifier = buttonModifier,
            colors = MiuixButtonColors(
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                disabledColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                contentColor = MaterialTheme.colorScheme.onSurface,
                disabledContentColor = MaterialTheme.colorScheme.onSurface,
            ),
        ) {
            UpdateButtonContent(
                busy = busy, progress = progress, state = state,
                label = label, textAlpha = textAlpha,
            )
        }
    } else {
        Button(
            onClick = onClick,
            enabled = enabled,
            shape = shape,
            contentPadding = contentPadding,
            colors = if (busy) activeColors else ButtonDefaults.buttonColors(),
            modifier = buttonModifier,
        ) {
            UpdateButtonContent(
                busy = busy, progress = progress, state = state,
                label = label, textAlpha = textAlpha,
            )
        }
    }
    }
}

/**
 * 更新按钮的 Row 内容（M3 Button 与 MIUIX 库 Button 共用）：
 * busy 态只显圆环；Available 态显下载图标；其余显状态文字。
 * ⚠️ ButtonSpinner 用 if(busy) 包裹不永远进 Row：spacedBy(8dp) 会在 0 宽节点与
 * 右侧 Icon 之间加间距，导致图标偏右。
 */
@Composable
private fun RowScope.UpdateButtonContent(
    busy: Boolean,
    progress: Float?,
    state: UpdateState,
    label: String,
    textAlpha: Float,
) {
    if (busy) {
        ButtonSpinner(busy = true, progress = progress)
    }
    if (state is UpdateState.Available) {
        Icon(
            painterResource(R.drawable.ic_ms_file_download),
            contentDescription = label,
            modifier = Modifier.size(ButtonDefaults.IconSize),
            tint = if (Radius.engine == ThemeEngine.MIUIX)
                MaterialTheme.colorScheme.onSurface
            else
                MaterialTheme.colorScheme.onPrimary,
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
        // ⚠️ 这里直接用 M3 CircularProgressIndicator，不走 JiabanCircularProgress 引擎分叉：
        // 本按钮本身就是 M3 Button（定制收缩动画），小尺寸圆环（14dp）必须与 M3 Button 内容测量
        // 严格对齐。MIUIX 库的 CircularProgressIndicator 不响应 Modifier.size(14.dp)（内部 defaultMinSize
        // 强制最小尺寸），且圆环在其 Box 内绘制不居中——真机实测偏右 + 直径 ~23dp。
        // 颜色按引擎分叉：MD3 按钮是 primary 实底 → onPrimary 白；MIUIX 按钮是浅灰底 → onSurface 深。
        val spinnerColor = if (Radius.engine == ThemeEngine.MIUIX)
            MaterialTheme.colorScheme.onSurface
        else
            MaterialTheme.colorScheme.onPrimary
        val p = progress
        if (p != null) {
            CircularProgressIndicator(
                progress = { p },
                modifier = Modifier.size(14.dp),
                strokeWidth = 1.5.dp,
                color = spinnerColor,
                trackColor = spinnerColor.copy(alpha = 0.3f),
            )
        } else {
            CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                strokeWidth = 1.5.dp,
                color = spinnerColor,
            )
        }
    }
}
/** 数据源选择弹窗：名称 + 地址点选即生效，行内可删（多项时）、底部可添加新地址（更新/节假日库共用） */
@Composable
private fun SourcePickerDialog(
    title: String,
    urls: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var showAdd by remember { mutableStateOf(false) }
    JiabanAlertDialog(containerColor = dialogContainerColor(), 
        onDismissRequest = onDismiss,
        title = { Text(title) },
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
                            .clip(engineShape(Radius.small))
                            .clickable(onClick = { onSelect(url) })
                            .padding(horizontal = Spacing.xs, vertical = Spacing.s),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        JiabanRadioButton(selected = url == selected, onClick = null)
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
                    shape = engineShape(Radius.pill),
                    contentPadding = PaddingValues(horizontal = Spacing.l, vertical = Spacing.s),
                ) {
                    Icon(painterResource(R.drawable.ic_ms_add), null, Modifier.size(IconSpec.inline))
                    Spacer(Modifier.width(Spacing.s))
                    Text(stringResource(R.string.datasource_add_url), style = MaterialTheme.typography.labelMedium)
                }
            }
        },
        confirmButton = {
            JiabanButton(
                text = stringResource(R.string.datasource_cancel),
                onClick = onDismiss,
                role = JiabanButtonRole.GHOST,
            )
        },
    )
    if (showAdd) {
        var text by remember { mutableStateOf("") }
        val t = text.trim()
        val dup = t.isNotEmpty() && t in urls
        JiabanAlertDialog(containerColor = dialogContainerColor(), 
            onDismissRequest = { showAdd = false },
            title = { Text(stringResource(R.string.datasource_add_url)) },
            text = {
                FloatingLabelTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = stringResource(R.string.datasource_add_url_hint),
                    isError = dup,
                    supportingText = if (dup) {
                        { Text(stringResource(R.string.datasource_add_url_dup)) }
                    } else null,
                )
            },
            confirmButton = {
                JiabanButton(
                    text = stringResource(R.string.datasource_ok),
                    onClick = {
                        onAdd(t)
                        showAdd = false
                    },
                    role = JiabanButtonRole.GHOST,
                    enabled = (t.startsWith("http://") || t.startsWith("https://")),
                )
            },
            dismissButton = {
                JiabanButton(
                    text = stringResource(R.string.datasource_cancel),
                    onClick = { showAdd = false },
                    role = JiabanButtonRole.GHOST,
                )
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
