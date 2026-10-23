package com.musclejournal.app.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.musclejournal.app.update.UpdateController
import com.musclejournal.app.update.UpdatePhase
import com.musclejournal.app.update.UpdateState
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
internal fun UpdateSettingsRow() {
    val context = LocalContext.current
    val controller = remember(context.applicationContext) {
        UpdateController.get(context.applicationContext)
    }
    val state by controller.state.collectAsState()
    val lifecycle = (context as? ComponentActivity)?.lifecycle
    val scope = rememberCoroutineScope()

    DisposableEffect(controller, lifecycle, scope) {
        var observation: Job? = null
        fun start() {
            if (observation == null) observation = scope.launch { controller.observeWhileResumed() }
        }
        fun stop() {
            observation?.cancel()
            observation = null
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> start()
                Lifecycle.Event.ON_PAUSE,
                Lifecycle.Event.ON_STOP,
                Lifecycle.Event.ON_DESTROY,
                -> stop()
                else -> Unit
            }
        }
        if (lifecycle == null || lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) start()
        lifecycle?.addObserver(observer)
        onDispose {
            stop()
            lifecycle?.removeObserver(observer)
        }
    }

    Column {
        Row(
            Modifier.fillMaxWidth()
                .heightIn(min = 64.dp)
                .clickable(enabled = state.canCheck || state.canInstall) {
                    if (state.canInstall) controller.install() else controller.check()
                },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(
                Modifier.weight(1f).padding(vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(if (state.canInstall) "安装更新 ${state.release?.version}" else "检查更新", fontSize = 15.sp)
                statusText(state)?.let { Text(it, color = Muted, fontSize = 12.sp) }
                if (state.phase == UpdatePhase.DOWNLOADING) {
                    val total = state.totalBytes
                    if (total != null) {
                        LinearProgressIndicator(
                            progress = { (state.downloadedBytes.toDouble() / total).toFloat().coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
            if (state.phase == UpdatePhase.IDLE) {
                Text(state.installedVersion, color = Muted, fontSize = 12.sp)
            }
            if (state.canCheck || state.canInstall) {
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = Muted)
            }
        }
        if (state.canCancelDownload) {
            TextButton(controller::cancelDownload) { Text("取消下载") }
        }
        HorizontalDivider(color = Outline)
    }

    val release = state.release
    if (state.phase == UpdatePhase.AVAILABLE && release != null) {
        AlertDialog(
            onDismissRequest = controller::dismissOffer,
            containerColor = Panel,
            title = { Text("发现新版本 ${release.version}") },
            text = {
                Text("当前版本 ${state.installedVersion}。\n是否下载 ${bytesText(release.apk.size)} 的新版本？更新保留本地数据。")
            },
            confirmButton = {
                TextButton(controller::download) { Text("下载更新") }
            },
            dismissButton = {
                TextButton(controller::dismissOffer) { Text("取消") }
            },
        )
    }
}

private fun statusText(state: UpdateState): String? = state.message ?: when (state.phase) {
    UpdatePhase.RECOVERING, UpdatePhase.IDLE -> null
    UpdatePhase.CHECKING -> "正在检查更新"
    UpdatePhase.NO_RELEASE -> "暂无正式版本"
    UpdatePhase.UP_TO_DATE -> "已是最新版本 · 当前 ${state.installedVersion}"
    UpdatePhase.AVAILABLE -> "新版本 ${state.release?.version} 可下载"
    UpdatePhase.PREPARING -> "正在获取 SHA-256 校验文件"
    UpdatePhase.DOWNLOADING -> buildString {
        append(if (state.paused) "系统下载暂停，等待恢复 · " else "正在下载 · ")
        append(bytesText(state.downloadedBytes))
        state.totalBytes?.let {
            append(" / ")
            append(bytesText(it))
        }
    }
    UpdatePhase.VERIFYING -> "正在校验文件、应用版本及签名"
    UpdatePhase.READY -> "安装包已通过校验 · 点击打开系统安装流程"
    UpdatePhase.PERMISSION -> "等待允许安装此来源 · 返回后继续安装"
    UpdatePhase.INSTALLER -> "等待系统安装确认 · 取消后可重试"
    UpdatePhase.ERROR -> "更新失败，请重试"
}

private fun bytesText(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> String.format(Locale.ROOT, "%.1f KiB", bytes / 1024.0)
    else -> String.format(Locale.ROOT, "%.1f MiB", bytes / (1024.0 * 1024))
}
