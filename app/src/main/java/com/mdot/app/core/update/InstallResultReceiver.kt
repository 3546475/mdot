package com.mdot.app.core.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.widget.Toast

/**
 * PackageInstaller Session 安装结果回调。
 * 系统安装完成后发广播到此 Receiver，简单提示结果即可。
 */
class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                // 需要用户确认安装：系统会自动弹出确认界面，这里不额外处理
                val confirmIntent = if (android.os.Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                }
                if (confirmIntent != null) {
                    try {
                        context.startActivity(confirmIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    } catch (e: Exception) {
                        // B5-04：原为空 catch——用户点「安装」无反应时无从排查，补日志
                        android.util.Log.w("InstallResult", "安装确认界面启动失败", e)
                    }
                }
            }
            PackageInstaller.STATUS_SUCCESS -> {
                // 安装成功，系统会提示"应用已安装"
            }
            else -> {
                // 安装失败，提示用户
                val msg = message ?: "安装失败"
                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            }
        }
    }
}
