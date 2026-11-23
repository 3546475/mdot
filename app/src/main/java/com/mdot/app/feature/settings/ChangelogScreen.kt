package com.mdot.app.feature.settings

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.mdot.app.R
import com.mdot.app.core.designsystem.Spacing
import com.mdot.app.core.designsystem.component.EmptyState
import com.mdot.app.core.designsystem.component.JiabanCircularProgress
import com.mdot.app.core.designsystem.component.JiabanTopBar
import com.mdot.app.core.designsystem.component.SectionCard
import com.mdot.app.core.designsystem.component.ShrinkFeedbackButton
import com.mdot.app.core.navigation.contentBottomPadding
import com.mdot.app.core.update.ChangelogRepository
import com.mdot.app.domain.model.ChangelogEntry
import com.mdot.app.domain.model.ChangelogGroup
import com.mdot.app.domain.model.ChangelogItem
import com.mdot.app.feature.settings.ChangelogViewModel.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 更新日志页 VM：内容来自**线上** CHANGELOG.md（不内置 assets 兜底），
 * 由 [ChangelogRepository] 负责拉取 + 解析 + 缓存。
 */
@HiltViewModel
class ChangelogViewModel @Inject constructor(
    private val repo: ChangelogRepository,
) : ViewModel() {

    /** 页面的三种形态：拉取中 / 有内容 / 拉不到 */
    sealed interface UiState {
        data object Loading : UiState

        /** [fromCache] = true 表示用的是本地缓存（网络失败或缓存仍新鲜） */
        data class Ready(val entries: List<ChangelogEntry>, val fromCache: Boolean) : UiState

        data class Failed(val message: String) : UiState
    }

    private val _state = MutableStateFlow<UiState>(UiState.Loading)
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        refresh()
    }

    /** 重新拉取（失败重试 / 下拉） */
    fun refresh() {
        viewModelScope.launch {
            _state.value = UiState.Loading
            _state.value = when (val r = repo.load()) {
                is ChangelogRepository.LoadResult.Success -> UiState.Ready(r.entries, r.fromCache)
                is ChangelogRepository.LoadResult.Failure -> UiState.Failed(r.message)
            }
        }
    }
}

/**
 * 更新日志页（二级页：关于 → 更新日志）。
 *
 * 内容只从线上拉（用户钦定：**不做内置 assets 兜底**）；拉不到时给「重试」而不是空白页。
 */
@Composable
fun ChangelogScreen(
    onBack: () -> Unit,
    vm: ChangelogViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize()) {
        JiabanTopBar(title = stringResource(R.string.changelog_title), onBack = onBack)
        when (val s = state) {
            UiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                JiabanCircularProgress()
            }

            is UiState.Failed -> Box(
                Modifier
                    .fillMaxSize()
                    .contentBottomPadding(showBottomBar = false),
                contentAlignment = Alignment.Center,
            ) {
                EmptyState(
                    icon = painterResource(R.drawable.ic_ms_warning),
                    title = stringResource(R.string.changelog_failed_title),
                    hint = s.message,
                    actionText = stringResource(R.string.changelog_retry),
                    onAction = { vm.refresh() },
                )
            }

            is UiState.Ready -> ChangelogBody(
                entries = s.entries,
                fromCache = s.fromCache,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** 更新日志正文：版本倒序（原文即最新在前），每个版本一个标题 + 分组卡 */
@Composable
private fun ChangelogBody(
    entries: List<ChangelogEntry>,
    fromCache: Boolean,
    modifier: Modifier = Modifier,
) {
    if (entries.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EmptyState(
                icon = painterResource(R.drawable.ic_ms_auto_awesome),
                title = stringResource(R.string.changelog_empty_title),
                hint = stringResource(R.string.changelog_empty_hint),
            )
        }
        return
    }
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .contentBottomPadding(showBottomBar = false),
        contentPadding = PaddingValues(
            start = Spacing.page,
            end = Spacing.page,
            top = Spacing.s,
            bottom = Spacing.xl,
        ),
    ) {
        if (fromCache) {
            item(key = "cache-hint") {
                Text(
                    stringResource(R.string.changelog_cache_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = Spacing.s),
                )
            }
        }
        entries.forEach { entry ->
            item(key = "v-${entry.version}") { VersionHeader(entry) }
            entry.groups.forEach { group ->
                item(key = "g-${entry.version}-${group.title}") { GroupCard(group) }
            }
        }
    }
}

/** 版本标题行：版本号 + 发布日期 */
@Composable
private fun VersionHeader(entry: ChangelogEntry) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            entry.version,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        if (entry.date.isNotBlank()) {
            Spacer(Modifier.width(Spacing.s))
            Text(
                entry.date,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 一个分组（新增/修复/…）：分组标题 + 条目列表 */
@Composable
private fun GroupCard(group: ChangelogGroup) {
    SectionCard(modifier = Modifier.padding(bottom = Spacing.m)) {
        Column {
            Text(
                group.title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(Spacing.s))
            group.items.forEachIndexed { index, item ->
                if (index > 0) Spacer(Modifier.height(Spacing.s))
                ItemBlock(item)
            }
        }
    }
}

/** 一个条目：标题（可空）+ 正文（可多行；子项以「· 」开头，缩进显示） */
@Composable
private fun ItemBlock(item: ChangelogItem) {
    Column(Modifier.fillMaxWidth()) {
        if (item.title.isNotBlank()) {
            Text(
                item.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
        }
        if (item.body.isNotBlank()) {
            if (item.title.isNotBlank()) Spacer(Modifier.height(Spacing.xs))
            item.body.split("\n").forEach { line ->
                val isSub = line.startsWith("·")
                Text(
                    line,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = if (isSub) Modifier.padding(start = Spacing.m) else Modifier,
                )
            }
        }
    }
}

/**
 * 首启「本次更新」引导：**一次性全屏卡片**（走页内覆盖层栈，不开系统窗口）。
 *
 * ⚠️ 根节点吞掉空白处点击：覆盖层的挡板会「点外部 ⇒ 关闭」，而这张卡片不该被误关；
 * 子级（LazyColumn）先消费 down ⇒ 滚动不受影响。
 */
@Composable
fun ChangelogGuideContent(
    entry: ChangelogEntry,
    onClose: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) { detectTapGestures { } },
        ) {
            // 顶部：标题 + 版本/日期
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(
                        top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + Spacing.l,
                        start = Spacing.page,
                        end = Spacing.page,
                        bottom = Spacing.s,
                    ),
            ) {
                Text(
                    stringResource(R.string.changelog_guide_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    buildString {
                        append(entry.version)
                        if (entry.date.isNotBlank()) {
                            append(" · ")
                            append(entry.date)
                        }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // 主体：本次更新的分组内容（可滚动）
            LazyColumn(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.page),
            ) {
                entry.groups.forEach { group ->
                    item(key = "guide-${group.title}") { GroupCard(group) }
                }
            }
            // 底部：知道了
            Box(
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(
                        start = Spacing.page,
                        end = Spacing.page,
                        top = Spacing.s,
                        bottom = Spacing.l,
                    ),
            ) {
                ShrinkFeedbackButton(
                    text = stringResource(R.string.changelog_guide_ok),
                    busy = false,
                    onClick = onClose,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
