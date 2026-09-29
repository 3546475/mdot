package com.mdot.app.core.designsystem.component

import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.mdot.app.core.designsystem.Radius
import com.mdot.app.core.designsystem.SheetBackdrop
import com.mdot.app.core.designsystem.dialogContainerColor
import com.mdot.app.core.designsystem.miuix.MiuixSourceBackdrop
import com.mdot.app.core.designsystem.sheetSpatialSpec
import com.mdot.app.domain.model.SheetBackdropMode
import com.mdot.app.domain.model.ThemeEngine
import top.yukonga.miuix.kmp.blur.blur
import top.yukonga.miuix.kmp.blur.drawBackdrop
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.VerticalDivider
import androidx.compose.ui.draw.clip
import com.mdot.app.core.designsystem.DialogSpec
import com.mdot.app.core.designsystem.Spacing
import androidx.compose.foundation.layout.Arrangement

/**
 * 共享录制源的**无门控**下发（宿主持有源即提供；毛玻璃开关只门控 [LocalBackdropGlassState]
 * 的玻璃视觉）。弹层/弹窗背景效果采样身后画面用——注意 AppRoot 下发的是**全画面源**
 * （含顶/底栏，NavHost 上另有一个给玻璃消费方的小源；两源不同 RenderNode，无自引用环）。
 */
val LocalBackdropSourceState = compositionLocalOf<BackdropBlurState?> { null }

/**
 * 对话框背景效果层（外观页「弹层背景」三档）。独立装饰窗口：不收事件、不抢焦点；
 * 关闭交互由其上的 M3 对话框窗口自身处理（点外/返回）。
 *
 * ⚠️ **渲染公式与 [SheetBackdropLayer]（底部弹层背景）严格同构**——2026-09-30 多轮返修
 * 教训：先照抄弹层、再谈差异。三层堆叠（从下到上）：
 * ① [SheetBackdrop.backdropScrim]×progress 黑色底衬（缩小档四周露出的空间感；缺了它
 *   四周就是"原来画面"）；② 背景快照：**graphicsLayer 外层管缩放+圆角裁切**、
 *   内层录层+BlurEffect 管模糊（模糊先于变换，与弹层 modifier 链同序）；
 * ③ [SheetBackdrop.contentScrim]×progress 顶层压暗。
 *
 * ⚠️ **弹窗不做缩小**（2026-09-30 用户要求）：开关在「模糊」或「模糊缩小」时，对话框一律
 * **只模糊不缩小**——缩小是底部弹层的语言，弹窗卡片自身另有 0.8→1 中心放大，背景再缩会与
 * 卡片分家。故本处 scale/clip 恒为 identity（缩小档令牌仍服务于 [SheetBackdropLayer]）。
 * 进场相位 **等快照就绪才起跑**（否则缩小过程在快照缺失的头几帧被吞 → "卡片突兀出现"）。
 *
 * 模糊实现随引擎：MIUIX = miuix-blur 库（drawBackdrop shader 管线，SDK 33+）、
 * MD3 = 自有 RenderEffect 管线；两路都不支持（API<31）回退纯压暗。
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DialogBackdropWindow(onDismissRequest: () -> Unit, progressValue: Float) {
    // 需求计数：对话框背景消费**全画面源**，挂着即让该源开始录制（卸载归零后源只透传省重录）。
    // 计数挂在源状态上（按源各自计数），与玻璃消费导航宿主小源互不牵连。
    val demandState = LocalBackdropSourceState.current
    DisposableEffect(demandState) {
        demandState?.let { it.demand++ }
        onDispose { demandState?.let { it.demand-- } }
    }
    val mode = LocalSheetBackdropMode.current
    val sourceState = LocalBackdropSourceState.current
    val blurMode = mode != SheetBackdropMode.DIM
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
            dismissOnClickOutside = false,
            dismissOnBackPress = false,
        ),
    ) {
        // 窗口动画由主题层管理（themes.xml：入场无、出场=淡出）——勿在此 setWindowAnimations(0)，
        // 那会连出场动画一起杀掉（2026-09-30 教训）
        val backdropView = LocalView.current
        val engineMiuix = Radius.engine == ThemeEngine.MIUIX && Build.VERSION.SDK_INT >= 33
        val density = LocalDensity.current
        val corner = SheetBackdrop.cornerRadius
        val cornerPx = with(density) { corner.toPx() }
        val bgColor = MaterialTheme.colorScheme.background
        val scrimColor = MaterialTheme.colorScheme.scrim
        // 弹窗背景**只模糊/压暗、不缩小**（见上方 ⚠️）：scale/clip 恒为 identity，
        // 保留变量是为了 MIUIX 的 layerBlock / MD3 的 graphicsLayer 两路共用同一段绘制链。
        val scale = 1f
        val clipProgress = 0f
        val blurPx = with(density) { SheetBackdrop.blurRadius.toPx() * progressValue }
        // ①/③ 两层压暗（与弹层同令牌同相位）
        val baseScrimAlpha = if (blurMode) SheetBackdrop.backdropScrim * progressValue else 0f
        val topScrimAlpha =
            (if (blurMode) SheetBackdrop.contentScrim else SheetBackdrop.contentScrimNoBlur) * progressValue

        Box(Modifier.fillMaxSize()) {
            // ① 黑色底衬：缩小档四周露出的部分（弹层的外层 background 同款）
            Box(
                Modifier.fillMaxSize().drawWithContent {
                    if (baseScrimAlpha > 0f) drawRect(color = scrimColor, alpha = baseScrimAlpha)
                },
            )
            // ② 背景快照：外层 graphicsLayer 管缩放 + 圆角裁切，内层录层+模糊
            if (blurMode && sourceState != null) {
                if (engineMiuix) {
                    // MIUIX：miuix-blur 库 shader 管线（layerBlock 同步缩放/圆角）
                    Box(
                        Modifier
                            .fillMaxSize()
                            .drawBackdrop(
                                backdrop = MiuixSourceBackdrop(sourceState, originView = backdropView),
                                shape = { RoundedCornerShape(corner) },
                                layerBlock = {
                                    scaleX = scale
                                    scaleY = scale
                                    if (clipProgress > 0f) {
                                        shape = RoundedCornerShape(cornerPx * clipProgress)
                                        clip = true
                                    }
                                },
                                effects = { blur(blurPx) },
                                enabled = progressValue > 0f,
                            ),
                    )
                } else {
                    // MD3：自有 RenderEffect 管线（录层 + BlurEffect，BackdropBlur 效果方同款）
                    val graphicsLayer = rememberGraphicsLayer()
                    var myCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
                    val blurEffect = remember(blurPx) { BlurEffect(blurPx, blurPx, TileMode.Clamp) }
                    Box(
                        Modifier
                            .fillMaxSize()
                            // 与弹层同序：外层变换（缩放+圆角裁切）、内层先把内容模糊
                            .then(
                                Modifier.graphicsLayer {
                                    scaleX = scale
                                    scaleY = scale
                                    if (clipProgress > 0f) {
                                        shape = RoundedCornerShape(cornerPx * clipProgress)
                                        clip = true
                                    }
                                },
                            )
                            .onGloballyPositioned { myCoords = it }
                            .drawWithContent {
                                val src = sourceState.layer
                                val srcCoords = sourceState.sourceCoords
                                val self = myCoords
                                if (progressValue > 0f && src != null && srcCoords != null &&
                                    self != null && self.isAttached && srcCoords.isAttached &&
                                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                                ) {
                                    // ⚠️ 跨窗口偏移 = 窗口内差值 + **窗口屏幕原点差**（帧取证定案：
                                    // positionInWindow 各自相对本窗原点，两窗原点因装饰嵌入/圆角屏不同 →
                                    // 漏掉即『快照偏右上』；启动期原点微调即『点击时画面抖动』。原点每帧现算）
                                    val dlgO = com.mdot.app.core.designsystem.miuix.windowScreenOrigin(backdropView)
                                    val actO = com.mdot.app.core.designsystem.miuix.windowScreenOrigin(
                                        sourceState.sourceView ?: return@drawWithContent,
                                    )
                                    val delta = dlgO - actO
                                    val offset = self.positionInWindow() - srcCoords.positionInWindow() + delta
                                    graphicsLayer.record(IntSize(size.width.toInt(), size.height.toInt())) {
                                        drawRect(color = bgColor, size = this.size)
                                        translate(-offset.x, -offset.y) { drawLayer(src) }
                                    }
                                    graphicsLayer.renderEffect = blurEffect
                                    drawLayer(graphicsLayer)
                                }
                            },
                    )
                }
            }
            // ③ 顶层压暗（弹层的内容压暗层同款）
            Box(
                Modifier.fillMaxSize().drawWithContent {
                    if (topScrimAlpha > 0f) drawRect(color = scrimColor, alpha = topScrimAlpha)
                },
            )
        }
    }
}

/**
 * 关掉 M3 对话框窗口的平台压暗：背景层已自带压暗，窗口 dim 叠乘会整片发黑
 * （docs/11 038 同坑）。窗口动画在 themes.xml 的 DialogWindowTheme 一并关闭。
 */
@Composable
private fun DisablePlatformDim() {
    val view = LocalView.current
    DisposableEffect(view) {
        (view.parent as? DialogWindowProvider)?.window?.setDimAmount(0f)
        onDispose { }
    }
}

/**
 * 对话框（引擎无关入口，26 处调用点统一走它）：**卡片仍是 M3 [AlertDialog] 本体**（观感零变化），
 * 另开装饰窗在其下渲染「弹层背景」效果（压暗/模糊/模糊缩小，见 [DialogBackdropWindow]）。
 * 参数面与 M3 AlertDialog 对齐（title/text/confirmButton/dismissButton/containerColor…）。
 */
@Composable
fun JiabanAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: @Composable (() -> Unit)? = null,
    icon: @Composable (() -> Unit)? = null,
    title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null,
    containerColor: Color = dialogContainerColor(),
    /**
     * 是否绘制**本对话框自己的背景效果**（压暗/模糊）。默认 true。
     * false 的用途：**从底部弹层里打开的对话框**（如记加班弹层的日期选择）——此时弹层背景已由
     * `SheetBackdropLayer` 呈现，本窗再画一层会"盖掉"它（用户 2026-09-30 报：「日期选择弹窗的背景
     * 会覆盖掉记加班弹窗的背景，让记加班弹窗无任何背景变化，之前是什么就显示什么」）。
     */
    backdrop: Boolean = true,
    properties: DialogProperties = DialogProperties(),
) {
    // 入场动画（卡片中心由小变大 + 渐显）与模糊渐强/背景缩小**共用同一 Animatable**：
    // ⚠️ 必须 Animatable(0f) 起步——animateFloatAsState 首次组合直接从目标值起步＝无过程
    //（『卡片突兀出现』根因）；快照就绪才起跑（否则缩小过程被快照缺失的头几帧吞掉）。
    val sourceState = LocalBackdropSourceState.current
    val openTick = remember { sourceState?.recordTick ?: 0 }
    // 快照**新鲜度**门控：入场必须等源在开窗后**重新录到一帧**再起跑——否则开场画的是上一次
    // 录制的旧画面，与此刻实时页有逐像素差异（实测 ~6px 级瞬移）= 用户看到的『点击时画面往下抖动』。
    // 等这次录制（recordTick 自增）后，快照 == 实时页，差异为零再开始放大/渐强。
    val snapshotReady = LocalSheetBackdropMode.current == SheetBackdropMode.DIM ||
        (sourceState?.layer != null && sourceState.recordTick > openTick)
    val progress = remember { Animatable(0f) }
    val progressSpec = sheetSpatialSpec<Float>()
    LaunchedEffect(snapshotReady) {
        if (snapshotReady) {
            // ⚠️ 再等约 6 帧（≈100ms）才起跑：宿主（活动窗）在开窗瞬间的头 1~3 帧仍处于布局/动画
            // 中间态，源此时录到的帧与稳定态有 ~16px 级差异 ⇒ 背景画面『弹一下再回来』
            //（2026-09-30 逐帧取证：首帧偏 16~17px、约 3 帧归零，与用户『几百毫秒弹动』吻合）。
            // 这 6 帧里快照不绘制（progress=0），画面即实时页本身 ⇒ 过渡无缝、坏帧不上屏。
            repeat(6) { withFrameNanos { } }
            progress.animateTo(1f, animationSpec = progressSpec)
        }
    }
    // 出场（反向）：拦截关闭 → 先反向播放同一条曲线（卡片缩回中心 + 背景渐弱）→ 再真正移除。
    // ⚠️ 只在**组合侧**做出场：窗口级退出动画会连带把系统默认入场动画（从下往上）带回来 = 抖动
    // （2026-09-30 dumpsys 实证：自定义 windowAnimationStyle 未生效、回落 wanim=0x103030a）。
    // 局限（如实记录）：取消/保存按钮由调用方状态直接移除对话框，组合侧拦不到，那两条路径仍是立即关闭。
    var closing by remember { mutableStateOf(false) }
    val requestClose: () -> Unit = { if (!closing) closing = true }
    LaunchedEffect(closing) {
        if (closing) {
            progress.animateTo(0f, animationSpec = progressSpec)
            onDismissRequest()
        }
    }
    // MIUIX：卡片标题**居中**（HyperOS 观感）；MD3 保持 M3 的起始对齐（不动被点名的另一侧）
    val titleSlot: (@Composable () -> Unit)? = when {
        title == null -> null
        Radius.engine == ThemeEngine.MIUIX -> {
            { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { title() } }
        }
        else -> title
    }
    // 背景效果窗**必须照建**：它兼任两职——① 登记录制需求（demand）驱动共享源录制；② 它是入场进度的
    // 起跑门控（snapshotReady 要求 recordTick 前进）。
    // ⚠️ backdrop=false 时**不能跳过整个窗口**（2026-09-30 回归：跳过它 ⇒ 源不录制 ⇒ progress 永为 0
    // ⇒ 卡片 alpha=0 看不见、而窗口已盖住屏幕 = 用户报「点日期选择没反应、弹窗根本没打开」）。
    // 正确做法：窗口照建、绘制归零——该函数内部所有绘制都乘 progressValue（模糊半径/两层遮罩/快照绘制），
    // 传 0f 即完全透明，demand 与进度门控都不受影响。
    DialogBackdropWindow(requestClose, if (backdrop) progress.value else 0f)
    // 卡片入场：中心放大 + 渐显（两引擎共用同一条曲线）
    val cardModifier = modifier.graphicsLayer {
        val p = progress.value
        scaleX = 0.8f + 0.2f * p
        scaleY = 0.8f + 0.2f * p
        alpha = p
    }
    if (Radius.engine == ThemeEngine.MIUIX) {
        // MIUIX：自绘卡片 + **整宽分栏按钮行**（HyperOS 观感：左取消 / 右确定 + 细分隔线）
        MiuixDialogCard(
            onDismissRequest = requestClose,
            cardModifier = cardModifier,
            containerColor = containerColor,
            icon = icon,
            title = title,
            text = text,
            confirmButton = confirmButton,
            dismissButton = dismissButton,
        )
    } else {
        // MD3：对话框内的 PRIMARY/SECONDARY 按「以前的」原生样式渲染（M3 Button / OutlinedButton）
        androidx.compose.runtime.CompositionLocalProvider(LocalDialogButtonNative provides true) {
        AlertDialog(
            onDismissRequest = requestClose,
            confirmButton = {
                DisablePlatformDim()
                confirmButton()
            },
            modifier = cardModifier,
            dismissButton = dismissButton,
            icon = icon,
            title = titleSlot,
            text = text,
            containerColor = containerColor,
            properties = properties,
        )
        }
    }
}

/**
 * MIUIX 对话框卡片（HyperOS 观感）：自绘卡片 + **整宽分栏按钮行**——
 * 左「取消」/ 右「确认」各占一半宽、中间一条细竖分隔线，按钮行上方一条横分隔线。
 *
 * 仅由 [JiabanAlertDialog] 在 MIUIX 引擎下调用（MD3 侧仍走 M3 [AlertDialog]，观感不动）。
 * 自有窗口（`Dialog`）但沿用主题 `DialogWindowTheme`（`windowAnimationStyle=@null`）= 无窗口动画，
 * 与背景效果窗的相位公式不受影响（抖动回归已经在案，勿在此重开窗口动画）。
 */
@Composable
private fun MiuixDialogCard(
    onDismissRequest: () -> Unit,
    cardModifier: Modifier,
    containerColor: Color,
    icon: (@Composable () -> Unit)?,
    title: (@Composable () -> Unit)?,
    text: (@Composable () -> Unit)?,
    confirmButton: @Composable () -> Unit,
    dismissButton: (@Composable () -> Unit)?,
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        DisablePlatformDim()
        val dividerColor = MaterialTheme.colorScheme.outlineVariant
        Column(
            modifier = cardModifier
                .padding(horizontal = Spacing.l)
                .widthIn(min = DialogSpec.minWidth, max = DialogSpec.maxWidth)
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.extraLarge)
                .background(containerColor),
        ) {
            Column(Modifier.padding(Spacing.xl)) {
                if (icon != null) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { icon() }
                    Spacer(Modifier.height(Spacing.m))
                }
                if (title != null) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { title() }
                }
                if (text != null) {
                    if (title != null) Spacer(Modifier.height(Spacing.s))
                    text()
                }
            }
            HorizontalDivider(color = dividerColor)
            // 按钮行扁平化：HyperOS 的对话框按钮是整宽纯文字格，不在文字下垫主题色圆角底
            androidx.compose.runtime.CompositionLocalProvider(LocalDialogButtonFlat provides true) {
            Row(Modifier.fillMaxWidth().height(DialogSpec.buttonRowHeight)) {
                if (dismissButton != null) {
                    // ⚠️ 取消/次要格必须用 **Row**（不是 Box）：该槽可能同时放**多颗**按钮
                    // （日期选择多选态 = 「清除选择 + 取消」）——Box 会把它们叠在同一处，
                    // 表现为两份文字重影的"乱码"（用户 2026-09-30 报告）。Row 并排放、各自居中 ✓。
                    // 本格**也下发** LocalDialogButtonFillCell（2026-09-30 用户报「日期弹窗取消按钮只能点文字」）：
                    // 单颗时铺满整格 = 整格可点；**多颗时由调用方把每颗各包一层 `Box(Modifier.weight(1f))`**
                    // （见 DayPickDialog 多选态）⇒ 各占半格、互不挤占（标记只被 MIUIX 分支消费，MD3 无影响）。
                    Row(
                        Modifier.weight(1f).fillMaxHeight(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // 取消/次要格：中性文字色（确认格保持主题强调色）
                        androidx.compose.runtime.CompositionLocalProvider(
                            LocalDialogButtonNeutral provides true,
                            LocalDialogButtonFillCell provides true,
                        ) { dismissButton() }
                    }
                    VerticalDivider(color = dividerColor)
                }
                // 确认格同样用 Row（万一也多颗时不叠加）；常规只一颗 ⇒ 保留"铺满整格 = 整格可点"
                Row(
                    Modifier.weight(1f).fillMaxHeight(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    androidx.compose.runtime.CompositionLocalProvider(
                        LocalDialogButtonFillCell provides true,
                    ) { confirmButton() }
                }
            }
            }
        }
    }
}
