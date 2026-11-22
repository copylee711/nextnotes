package com.nextnotes.ui.sync

import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nextnotes.sync.SyncCenter
import com.nextnotes.sync.SyncConfig
import com.xnotes.ui.theme.LocalPalette
import com.xnotes.ui.theme.toComposeColor
import kotlinx.coroutines.launch

/**
 * The sync part of the settings: where it stands, a button to sync now, and the server settings.
 * [root] is the notes folder that syncs; [onChanged] runs when a sync changed files here.
 */
@Composable
fun SyncSection(root: String?, showTitle: Boolean = true, onChanged: () -> Unit) {
    val context = LocalContext.current
    val palette = LocalPalette.current
    val dim = palette.textDim.toComposeColor()
    remember { SyncCenter.load(context) }
    var editing by remember { mutableStateOf(false) }
    var version by remember { mutableStateOf(0) }
    val config = remember(version) { SyncCenter.config(context) }

    if (showTitle) Text("多设备同步", color = palette.text.toComposeColor(), fontSize = 15.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
    Text(
        "通过 WebDAV（如坚果云、Nextcloud、群晖或自建服务）在平板、手机和电脑之间同步笔记文件夹。两边都改过的笔记会保留两份，不会丢。",
        color = dim, fontSize = 12.sp,
    )
    if (config == null) {
        TextButton(onClick = { editing = true }) { Text("设置 WebDAV 同步", fontSize = 13.sp) }
    } else {
        Text("${config.folderUrl}（${config.user}）", color = palette.text.toComposeColor(), fontSize = 13.sp)
        val status = SyncCenter.status
        val line = when {
            SyncCenter.running -> "正在同步…"
            status == null -> "还没有同步过"
            SyncCenter.lastSync > 0 -> "${ago(SyncCenter.lastSync)}：$status"
            else -> status
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (SyncCenter.running) {
                CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                Spacer(Modifier.size(6.dp))
            }
            Text(line, color = dim, fontSize = 12.sp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { SyncCenter.sync(context, root, onChanged) }, enabled = !SyncCenter.running) { Text("立即同步", fontSize = 13.sp) }
            TextButton(onClick = { editing = true }) { Text("修改设置", fontSize = 13.sp) }
        }
    }
    if (editing) {
        SyncDialog(config, onDismiss = { editing = false; version++ })
    }
}

private fun ago(t: Long): String {
    val min = ((System.currentTimeMillis() - t) / 60000).toInt()
    return when {
        min < 1 -> "刚刚"
        min < 60 -> "$min 分钟前"
        min < 60 * 24 -> "${min / 60} 小时前"
        else -> "${min / 60 / 24} 天前"
    }
}

@Composable
private fun SyncDialog(initial: SyncConfig?, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var server by remember { mutableStateOf(initial?.server ?: "https://") }
    var user by remember { mutableStateOf(initial?.user.orEmpty()) }
    var password by remember { mutableStateOf("") }
    var folder by remember { mutableStateOf(initial?.folder ?: "NextNotes") }
    var auto by remember { mutableStateOf(initial?.auto ?: true) }
    var result by remember { mutableStateOf<String?>(null) }
    val hasPassword = remember { SyncCenter.hasPassword(context) }
    val dim = LocalPalette.current.textDim.toComposeColor()
    fun current() = SyncConfig(server.trim(), user.trim(), folder.trim().trim('/').ifEmpty { "NextNotes" }, auto)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("WebDAV 同步") },
        text = {
            com.nextnotes.ui.KeepBarsHidden()
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(server, { server = it }, label = { Text("服务器地址") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
                if (server.trim().startsWith("http://")) {
                    Text("http 地址不加密，密码和笔记会明文传输，只建议在自己的局域网里用。", color = LocalPalette.current.danger.toComposeColor(), fontSize = 12.sp)
                }
                OutlinedTextField(user, { user = it }, label = { Text("用户名") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    password, { password = it }, label = { Text(if (hasPassword) "密码（已保存，留空不变）" else "密码（建议用应用密码）") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                )
                OutlinedTextField(folder, { folder = it }, label = { Text("服务器上的文件夹") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("自动同步")
                        Text("打开应用和关闭笔记时同步", color = dim, fontSize = 12.sp)
                    }
                    Switch(auto, { auto = it })
                }
                Text("密码加密保存在本机。同步的是首页的笔记文件夹；各文件夹的颜色标记、置顶和回收站留在各自设备上。", color = dim, fontSize = 12.sp)
                result?.let { Text(it, fontSize = 13.sp) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                SyncCenter.save(context, current(), password)
                onDismiss()
            }, enabled = server.startsWith("http") && user.isNotBlank() && (password.isNotEmpty() || hasPassword)) { Text("保存") }
        },
        dismissButton = {
            Row {
                if (initial != null) TextButton(onClick = { SyncCenter.forget(context); onDismiss() }) { Text("停用") }
                TextButton(onClick = {
                    result = "正在连接…"
                    scope.launch { result = SyncCenter.test(context, current(), password) }
                }) { Text("测试") }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        },
    )
}

/**
 * Where syncing stands, for the foot of the home sidebar: a cloud and a few words, or nothing
 * while sync is not set up.
 */
@Composable
fun SyncBadge(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val palette = LocalPalette.current
    remember { SyncCenter.load(context) }
    if (remember(SyncCenter.status, SyncCenter.running) { SyncCenter.config(context) } == null) return
    val status = SyncCenter.status
    val failed = status?.startsWith("同步失败") == true
    val line = when {
        SyncCenter.running -> "正在同步…"
        failed -> "同步失败"
        SyncCenter.lastSync > 0 -> "已同步 · ${ago(SyncCenter.lastSync)}"
        else -> "还没有同步过"
    }
    Row(
        modifier.fillMaxWidth().clip(androidx.compose.foundation.shape.RoundedCornerShape(14.dp))
            .background(palette.panel.toComposeColor()).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.material3.Icon(
            if (failed) androidx.compose.material.icons.Icons.Outlined.CloudOff else androidx.compose.material.icons.Icons.Outlined.CloudDone,
            null, tint = if (failed) palette.danger.toComposeColor() else Color(if (palette.isDark) 0xFFC8F169 else 0xFF1F9D55),
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.size(10.dp))
        Text(line, color = palette.text.toComposeColor(), fontSize = 13.sp, maxLines = 1)
    }
}
