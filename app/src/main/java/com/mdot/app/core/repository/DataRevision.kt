package com.mdot.app.core.repository

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 数据版本号（内存态）：大规模数据替换（云端恢复导入等）后 bump，
 * 订阅它的 ViewModel（首页/统计）会强制全量重算——比 touch() 语义准确，
 * 不会误触自动备份。
 */
@Singleton
class DataRevision @Inject constructor() {

    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version.asStateFlow()

    fun bump() {
        _version.value += 1
    }
}
