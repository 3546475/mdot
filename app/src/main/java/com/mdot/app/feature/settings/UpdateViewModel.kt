package com.mdot.app.feature.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mdot.app.BuildConfig
import com.mdot.app.core.update.UpdateInfo
import com.mdot.app.core.update.UpdateRepository
import com.mdot.app.core.util.AppResult
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 更新检查/下载安装的 UI 状态。错误与「已是最新」等短提示走 [notice]（行内文字）。 */
sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class Available(val info: UpdateInfo) : UpdateState
    data class Downloading(val progress: Int) : UpdateState
}

@HiltViewModel
class UpdateViewModel @Inject constructor(
    private val updateRepo: UpdateRepository,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice.asStateFlow()

    /** 拉取 update.json 并比对版本。 */
    fun check() = viewModelScope.launch {
        _state.value = UpdateState.Checking
        _notice.value = null
        when (val r = updateRepo.check()) {
            is AppResult.Success -> {
                val info = r.data
                if (info == null) {
                    _state.value = UpdateState.Idle
                    _notice.value = "当前已是最新版本（v${BuildConfig.VERSION_NAME}）"
                } else {
                    _state.value = UpdateState.Available(info)
                }
            }
            is AppResult.Failure -> {
                _state.value = UpdateState.Idle
                _notice.value = "检查更新失败，请稍后再试"
            }
        }
    }

    /** 下载（已下载则复用）并调起安装；未授权「安装未知应用」时引导去系统设置。 */
    fun downloadAndInstall() {
        val info = (_state.value as? UpdateState.Available)?.info ?: return
        viewModelScope.launch {
            _state.value = UpdateState.Downloading(0)
            when (val r = updateRepo.download(info) { p -> _state.value = UpdateState.Downloading(p) }) {
                is AppResult.Success -> {
                    if (updateRepo.canInstall()) {
                        val ok = updateRepo.install(r.data)
                        _state.value = UpdateState.Idle
                        if (!ok) _notice.value = "无法调起安装器，请稍后重试"
                    } else {
                        // 回到可更新弹窗：授权后再次点击会直接复用已下载的包安装
                        _state.value = UpdateState.Available(info)
                        updateRepo.openInstallPermissionSettings()
                        _notice.value = "请先在系统设置中允许安装未知应用，再点击「立即更新」"
                    }
                }
                is AppResult.Failure -> {
                    _state.value = UpdateState.Available(info)
                    _notice.value = "下载失败，请检查网络后重试"
                }
            }
        }
    }

    fun dismiss() {
        if (_state.value !is UpdateState.Downloading) _state.value = UpdateState.Idle
    }

    fun clearNotice() {
        _notice.value = null
    }
}
