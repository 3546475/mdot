package com.mdot.app.core.designsystem.component

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.mdot.app.R
import com.mdot.app.core.designsystem.OptionPillSpec
import com.mdot.app.core.designsystem.Spacing
import kotlinx.coroutines.delay

/**
 * 药丸的一个选项 —— 同时也是列表卡里的一行：圆图标 + 文案 + 开关状态。
 * 图标**两处共用**（药丸读数里那小叠圆、列表行左侧那个圆），正是它让药丸的读数成为「哪些开着」而不是一个数字。
 */
data class OptionPillItem(
    val id: String,
    @DrawableRes val iconRes: Int,
    val label: String,
    val checked: Boolean,
)

/**
 * ══ 选项药丸 + 列表卡 ════════════════════════════════════════════════
 * 一颗实底药丸，点开在它下方撑出一张实底列表卡。形态移植自 **Bencho 的 Assignees 组件**，
 * 全部数值、令牌映射表与「这个数字为什么是这个值」的注释见 [OptionPillSpec]。
 *
 * **药丸就是读数**（用户 2026-09-22 定：**药丸上不再上屏标题**，只剩读数与箭头——原版 Bencho 本来就是如此）：
 * 左侧叠放「已开启项」的圆形图标，全关时退化为一个状态词。
 * 没有计数徽标、也没有「已开启 3 项」的副标题 —— 图标本身就说清了哪些开着：
 * 一个数字只告诉你数量，一叠图标才告诉你选对没有。文字标题改从**无障碍名称**走
 * （[title] → `contentDescription`），屏幕阅读器仍能报出这块叫什么。
 *
 * 与 Bencho 原版的三处**刻意不同**（都是本仓库规则要求，不是遗漏）：
 * 1. 展开方式 —— Bencho 是绝对定位悬浮展开、且常态预留整块高度（换来开合时下方一动不动）；
 *    本移植用**就地撑开**（高度增长），代价是下方内容随之下移，换来不浪费常态高度、
 *    也不遮挡下方「预览」区块（Android 手风琴惯例）。
 * 2. 行的右侧保留 **M3 Switch**，不用 Bencho 的圆角方形勾选框 —— 用户取舍：只改观感、不改交互习惯。
 *    行的容器/圆角/按压填充/错峰入场仍照搬。
 * 3. 动效系数换成 `motionScheme` 三档（见 [OptionPillSpec] 的动效映射段）。
 *
 * **不接「点外面收起」**（Bencho 把它删掉的理由值得照抄）：它是下拉框的规矩，不是这一块的。
 * 实测页面上的任意一次点击都会把列表收掉、且没有任何东西能把它叫回来 —— 于是这一块在剩下的时间里
 * 只剩一颗药丸，等于只演示了组件的三分之一。药丸自己仍是开关，仍可被真正在用它的人收起；
 * 去掉的只是「你对**别的**东西做的事，把这一个关掉了」。
 *
 * **颜色与分区卡同色**（用户 2026-09-22 定：这一块的底色要与其它卡片一致）——
 * 原版这里是 `--fill-slab`（比区块底色 `--card` 亮/深一档）；本项目不引第三层底色，
 * 因为这一块**没有分区卡**，药丸与列表卡自己就是这块的「板」（见 [slabColor]）。
 */
@Composable
fun OptionPillCard(
    /** 药丸的标题：「More」（原「更多选项」，2026-09-22 改名）——**不上屏**（见 [OptionPill]），但两处用到：
     *  ① 无障碍名称（`contentDescription`）；② **全关时当占位文字**（用户 2026-09-22 定） */
    title: String,
    items: List<OptionPillItem>,
    onToggle: (id: String, checked: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    initiallyExpanded: Boolean = false,
) {
    var expanded by rememberSaveable { mutableStateOf(initiallyExpanded) }
    val motion = MaterialTheme.motionScheme
    val effectsSpec = motion.defaultEffectsSpec<Float>()
    val sizeSpec = motion.defaultSpatialSpec<IntSize>()
    val intOffsetSpec = motion.defaultSpatialSpec<IntOffset>()
    val density = LocalDensity.current
    val dropPx = with(density) { OptionPillSpec.cardEnterOffset.roundToPx() }
    val risePx = with(density) { OptionPillSpec.cardExitOffset.roundToPx() }

    // 容器跟内容宽：列表卡自己 `fillMaxWidth`（所以展开时满宽），收起时就只有药丸那么宽
    Column(modifier) {
        OptionPill(
            title = title,
            items = items,
            expanded = expanded,
            onExpandedChange = { expanded = it },
        )
        // ── 列表卡 ───────────────────────────────────────────────
        // 就地把药丸下方撑开：`expandVertically(expandFrom = Top)` 就是「从药丸底下往外长」，
        // shrink 同理收回去 —— 开合都从药丸那一侧发生，不会像从中间弹出一块。
        // （Bencho 是从左上角做非等比缩放；Compose 的 scaleIn 只能等比，而高度增长已经说了同一件事。）
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(effectsSpec) +
                slideInVertically(intOffsetSpec) { -dropPx } +
                expandVertically(animationSpec = sizeSpec, expandFrom = Alignment.Top),
            exit = fadeOut(effectsSpec) +
                slideOutVertically(intOffsetSpec) { -risePx } +
                shrinkVertically(animationSpec = sizeSpec, shrinkTowards = Alignment.Top),
        ) {
            Column {
                // 药丸与列表卡之间要留出距离（用户 2026-09-22：两者要分开）——
                // 两块「板」同色，不隔一段空白就会连成一块说不出边界的东西。
                // 间距放在 AnimatedVisibility **里面**：收起时它跟着一起收掉，不留下一条白带。
                Spacer(Modifier.height(OptionPillSpec.pillCardGap))
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(OptionPillSpec.slabRadius))
                        .background(slabColor())
                        .padding(OptionPillSpec.listPadding),
                ) {
                    items.forEachIndexed { index, item ->
                        key(item.id) {
                            OptionRow(
                                item = item,
                                index = index,
                                onToggle = { onToggle(item.id, it) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 药丸与列表卡的实底色 —— **与分区卡 `SectionCard` 同色**（用户 2026-09-22 定）。
 *
 * 原版 Bencho 这里是 `--fill-slab`（比区块底色 `--card` 亮/深一档），因为它是叠在区块底色**上**的一层；
 * 本项目这一块**没有分区卡**（见 [OptionPillCard] 的说明），药丸与列表卡自己就是这块的「板」，
 * 所以取的就是分区卡那一档 `surfaceContainer`——页面底色（`surface`）才是原版那个 `--card`。
 */
@Composable
private fun slabColor(): Color = MaterialTheme.colorScheme.surfaceContainer

/**
 * 药丸本体：读数（叠压圆 / 状态词）→ 箭头。
 *
 * **药丸上不上屏标题**（用户 2026-09-22 定）：它只剩「现在开着哪些」这个读数与一个箭头。
 * 原版 Bencho 本来就是如此；文字标题改从无障碍名称走（[title] → `contentDescription`）。
 *
 * 宽度**从数量算出来**，不是量出来的：Bencho 原文 —— Framer 的 `layout` 靠测量屏幕矩形做动画，
 * 而那一墙的每个块都按自身尺寸的比例绘制；由数量算出来的宽度在任何缩放下都是同一个数，
 * 而且不用知道自己在哪儿就能做过渡。这里同理：叠几个圆 → 轨宽就是几个圆的算式，交给一条动画。
 *
 * **药丸不撑满整行**：它是一颗药丸，不是一个条 —— 读数与箭头贴着站在一起。
 */
@Composable
private fun OptionPill(
    /** 只用于无障碍名称（`contentDescription`）+ 全关时的占位文字，平时不上屏 */
    title: String,
    items: List<OptionPillItem>,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
) {
    val motion = MaterialTheme.motionScheme
    val slab = slabColor()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val checkedCount = items.count { it.checked }
    val density = LocalDensity.current
    val dropPx = with(density) { OptionPillSpec.readoutDropOffset.roundToPx() }
    val exitRisePx = with(density) { OptionPillSpec.readoutExitRise.roundToPx() }

    // 读数轨的宽：一个圆，或 n 个圆叠掉 (n-1)×叠压量，再加两端向外的环
    val railWidth by animateDpAsState(
        targetValue = if (checkedCount == 0) {
            0.dp
        } else {
            OptionPillSpec.readoutSize +
                (OptionPillSpec.readoutSize - OptionPillSpec.readoutLap) * (checkedCount - 1) +
                OptionPillSpec.readoutRing * 2
        },
        animationSpec = motion.defaultSpatialSpec<Dp>(),
        label = "pillRailWidth",
    )
    // ── 箭头就是按压态 ───────────────────────────────────────
    // 药丸不投影也不描边（它和列表卡是同一块「板」的两种形态，底下的卡片底色已经把两者分开了）。
    // 那药丸拿什么回应手指？在「整体就是形状」的控件上再塞一块填充，等于凭空多出一个形状；
    // 于是改用同一句话说给箭头听：常态 40% 墨色，按下/展开提到 75%。
    val chevronColor by animateColorAsState(
        targetValue = MaterialTheme.colorScheme.onSurface.copy(
            alpha = if (pressed || expanded) OptionPillSpec.chevronActiveAlpha
            else OptionPillSpec.chevronAlpha,
        ),
        animationSpec = motion.defaultEffectsSpec(),
        label = "pillChevron",
    )
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = motion.fastSpatialSpec<Float>(),
        label = "pillChevronRotation",
    )

    Row(
        Modifier
            .height(OptionPillSpec.pillHeight)
            // 缩放层必须包住后面的实底与内容：graphicsLayer 只作用于它**之后**的那一层
            .pressScale(interaction)
            .clip(RoundedCornerShape(OptionPillSpec.slabRadius))
            .background(slab)
            // 药丸上已无文字（见本函数 KDoc），无障碍名称改从这里给：
            // 屏幕阅读器仍能报出「More，按钮」，不会变成一颗只有图标的无名控件。
            .semantics { contentDescription = title }
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                role = Role.Button,
            ) { onExpandedChange(!expanded) }
            .padding(
                start = OptionPillSpec.pillPaddingStart,
                end = OptionPillSpec.pillPaddingEnd,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 读数位：有开启项时是一叠圆，全关时是一句状态词 —— 两者占同一个位置，不会空出一截
        Box(contentAlignment = Alignment.CenterStart) {
            Box(
                Modifier
                    .width(railWidth)
                    .height(OptionPillSpec.readoutSize),
                contentAlignment = Alignment.CenterStart,
            ) {
                var slot = 0
                items.forEachIndexed { index, item ->
                    // 第几枚就排第几个坑：坑位由「它前面有几个开着」决定，
                    // 所以撤掉一枚时后面的圆是挪过去、不是跳过去（未开启的项也算出自己的将来位，见 OptionReadout）
                    val mySlot = slot
                    if (item.checked) slot++
                    key(item.id) {
                        OptionReadout(
                            item = item,
                            slot = mySlot,
                            visible = item.checked,
                            // 反向 z 序：第一个圆在最上层，栈才从左往右读、与列表同向。
                            // 反过来画的话，最后来的会盖住之前所有圆，加第四个人看起来像丢了前三个。
                            zIndex = (items.size - mySlot).toFloat(),
                            ringColor = slab,
                            dropPx = dropPx,
                            exitRisePx = exitRisePx,
                        )
                    }
                }
            }
            // 读数位空着时的占位文字（抽成独立 composable 不只是为了整洁：
            // 直接在 Row 的 content 里写 AnimatedVisibility 会和已废弃的 RowScope 重载撞上）
            EmptyReadoutLabel(text = title, visible = checkedCount == 0)
        }
        // 药丸是 hug 宽的：它是一颗药丸，不是一个条
        Spacer(Modifier.width(OptionPillSpec.pillGap))
        Icon(
            painterResource(R.drawable.ic_ms_expand_more),
            contentDescription = null,
            tint = chevronColor,
            modifier = Modifier
                .size(OptionPillSpec.chevronSize)
                .graphicsLayer { rotationZ = chevronRotation },
        )
    }
}

/**
 * 读数里的一枚圆：28dp 的 tonal 小底 + 图标，外面垫一圈「它背后是什么颜色」的环。
 *
 * 环不是装饰、也不是白描边：它就是**两个圆之间的缝**，缝里该是什么就是什么 ——
 * 写死白色的话，深色模式下这叠圆会长出一圈光晕（Bencho 原话）。
 */
@Composable
private fun OptionReadout(
    item: OptionPillItem,
    slot: Int,
    visible: Boolean,
    zIndex: Float,
    ringColor: Color,
    dropPx: Int,
    exitRisePx: Int,
) {
    val motion = MaterialTheme.motionScheme
    val spatialSpec = motion.defaultSpatialSpec<Float>()
    val effectsSpec = motion.defaultEffectsSpec<Float>()
    val x by animateDpAsState(
        targetValue = (OptionPillSpec.readoutSize - OptionPillSpec.readoutLap) * slot,
        animationSpec = motion.defaultSpatialSpec<Dp>(),
        label = "readoutSlot",
    )
    val outer = OptionPillSpec.readoutSize + OptionPillSpec.readoutRing * 2

    Box(
        Modifier
            .offset(x = x)
            .zIndex(zIndex)
            .size(outer),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedVisibility(
            visible = visible,
            // 一枚圆是**落下来**的，不是淡进来：从略高处带一点转身落下、在静止位过冲一下，
            // 于是「加一个」读起来像把一枚筹码放上去。退场是同一个动作反过来、而且更快 ——
            // 你是在撤一个名字，不是在观赏动画。
            enter = fadeIn(effectsSpec) +
                scaleIn(spatialSpec, initialScale = OptionPillSpec.readoutEnterScale) +
                slideInVertically(motion.defaultSpatialSpec()) { -dropPx },
            exit = fadeOut(effectsSpec) +
                scaleOut(spatialSpec, targetScale = OptionPillSpec.readoutEnterScale) +
                slideOutVertically(motion.defaultSpatialSpec()) { -exitRisePx },
        ) {
            // 转身是这枚圆与「一个会缩放的圆」的全部区别：圆缩放还是圆，边缩边转才是一件东西。
            // 三个状态各有一个角度（进门 -22°、在位 0°、离场 +14°），而不是进出共用一个值。
            val rotation by transition.animateFloat(
                transitionSpec = { spatialSpec },
                label = "readoutRotation",
            ) { state ->
                when (state) {
                    EnterExitState.PreEnter -> OptionPillSpec.readoutEnterRotation
                    EnterExitState.Visible -> 0f
                    EnterExitState.PostExit -> OptionPillSpec.readoutExitRotation
                }
            }
            Box(
                Modifier
                    .graphicsLayer { rotationZ = rotation }
                    .size(outer)
                    .background(ringColor, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(OptionPillSpec.readoutSize)
                        .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painterResource(item.iconRes),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(OptionPillSpec.readoutIconSize),
                    )
                }
            }
        }
    }
}

/**
 * 读数位空着时的占位文字 —— 用**标题**（「More」），不是状态词。
 *
 * ⚠️ **与原版相反的有意取舍**（用户 2026-09-22 定）：原版空态写的是状态（"Unassigned"），
 * 因为药丸上本来没有别的字、状态词就是它的全部内容；本项目已经把标题从药丸上撤了（见 [OptionPill]），
 * 全关时若只剩「未开启」，一个脱离上下文的空药丸就说不清自己是什么东西了。
 * 所以在**没有读数可报**时把标题还回来当占位：有图标时它退场（旁边摆着三张图再配一句名字，
 * 那是控件在自我介绍、不是在回答）。
 *
 * 单独抽成一个 composable：在 Row 的 content 里直接写 AnimatedVisibility 会撞上已废弃的
 * `RowScope.AnimatedVisibility` 重载（隐式接收者歧义，编译不过）。
 */
@Composable
private fun EmptyReadoutLabel(text: String, visible: Boolean) {
    val motion = MaterialTheme.motionScheme
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(motion.defaultEffectsSpec()),
        exit = fadeOut(motion.defaultEffectsSpec()),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = OptionPillSpec.emptyTextAlpha),
            maxLines = 1,
        )
    }
}

/**
 * 列表卡里的一行：圆图标 + 文案 + Switch。
 *
 * 整行可点（不是只有开关可点）：Bencho 的行是一整个可达区域，右侧那个标记只是它末端的状态显示。
 * 行的反馈是**一块墨色填充**、不是涟漪 —— Bencho 分 hover 0.05 / focus-visible 0.07；
 * Android 没有 hover，按下即等价于它的焦点态，故取 0.07（见 [OptionPillSpec.rowPressedAlpha]）。
 */
@Composable
private fun OptionRow(
    item: OptionPillItem,
    index: Int,
    onToggle: (Boolean) -> Unit,
) {
    val motion = MaterialTheme.motionScheme
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val fill by animateColorAsState(
        targetValue = if (pressed) {
            MaterialTheme.colorScheme.onSurface.copy(alpha = OptionPillSpec.rowPressedAlpha)
        } else {
            Color.Transparent
        },
        animationSpec = motion.defaultEffectsSpec(),
        label = "optionRowFill",
    )

    // ── 错峰入场 ─────────────────────────────────────────────
    // 四行一起出现是一个面板；四行依次到位，才是有人把一张名单递给你。
    // 间隔 40ms「能感觉到、但不必等」（值见 [OptionPillSpec.rowStaggerMillis]）。
    val appear = remember { Animatable(0f) }
    val enterPx = with(LocalDensity.current) { OptionPillSpec.rowEnterOffset.toPx() }
    LaunchedEffect(Unit) {
        delay(OptionPillSpec.rowStaggerMillis * index)
        appear.animateTo(1f, motion.defaultSpatialSpec())
    }

    Row(
        Modifier
            .fillMaxWidth()
            .height(OptionPillSpec.rowHeight)
            // 绘制期读取、只读不写（写会自失效 → 每帧重录，见 docs/11 046）
            .graphicsLayer {
                val progress = appear.value
                alpha = progress.coerceIn(0f, 1f)
                translationY = (1f - progress) * enterPx
            }
            .clip(RoundedCornerShape(OptionPillSpec.rowRadius))
            .background(fill)
            .toggleable(
                value = item.checked,
                role = Role.Switch,
                interactionSource = interaction,
                // 反馈就是这块填充本身，不再叠涟漪（涟漪会盖在填充上，让「按下」看起来有两层）
                indication = null,
                onValueChange = onToggle,
            )
            .padding(horizontal = OptionPillSpec.rowPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(OptionPillSpec.readoutSize)
                .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(item.iconRes),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(OptionPillSpec.readoutIconSize),
            )
        }
        Spacer(Modifier.width(Spacing.m))
        Text(
            item.label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Switch(checked = item.checked, onCheckedChange = null)
    }
}
