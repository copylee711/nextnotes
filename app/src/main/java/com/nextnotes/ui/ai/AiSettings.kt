package com.nextnotes.ui.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nextnotes.ai.AiPresets
import com.nextnotes.ai.AiProfile
import com.nextnotes.ai.ProfileStore
import com.nextnotes.ai.Protocol
import com.xnotes.ui.theme.LocalPalette
import com.xnotes.ui.theme.toComposeColor
import kotlinx.coroutines.launch

/** The list of AI services, and the editor for one. */
@Composable
fun AiSettingsDialog(state: AiPaneState, onClose: () -> Unit) {
    var editing by remember { mutableStateOf<AiProfile?>(null) }
    // Opening with nothing configured goes straight to adding one; with ChatGPT on offer, the list
    // comes first so signing in is a choice beside adding a key.
    var adding by remember { mutableStateOf(false) }
    val target = editing
    if (target != null && target.protocol == Protocol.CHATGPT) {
        ChatGptModelPicker(state, target) { editing = null; state.profilesChanged() }
        return
    }
    if (target != null || adding) {
        ProfileEditor(
            state,
            initial = target,
            onDone = {
                editing = null
                adding = false
                state.profilesChanged()
                if (state.profiles.profiles().isEmpty()) onClose()
            },
        )
        return
    }
    val palette = LocalPalette.current
    val version = state.profilesVersion
    val list = remember(version) { state.profiles.profiles() }
    val active = remember(version) { state.profiles.active()?.id }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("AI 服务") },
        text = {
            com.nextnotes.ui.KeepBarsHidden()
            Column(Modifier.verticalScroll(rememberScrollState())) {
                ChatGptCard(state)
                for (p in list) {
                    Row(
                        Modifier.fillMaxWidth().clickable { state.profiles.setActive(p.id); state.profilesChanged() },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = p.id == active, onClick = { state.profiles.setActive(p.id); state.profilesChanged() })
                        Column(Modifier.weight(1f)) {
                            Text(p.name, fontSize = 15.sp)
                            Text(
                                "${p.model} · ${when {
                                    p.protocol == Protocol.CHATGPT -> "使用 ChatGPT 套餐"
                                    state.keys.has(p.id) -> "已保存密钥"
                                    else -> "无密钥"
                                }}",
                                fontSize = 12.sp, color = palette.textDim.toComposeColor(),
                            )
                        }
                        IconButton(onClick = { editing = p }) { Icon(Icons.Outlined.Edit, "编辑") }
                        IconButton(onClick = {
                            if (p.protocol == Protocol.CHATGPT) {
                                state.signOutChatGpt()
                            } else {
                                state.profiles.delete(p.id)
                                state.keys.remove(p.id)
                                state.profilesChanged()
                            }
                        }) { Icon(Icons.Outlined.Delete, "删除") }
                    }
                }
                Text(
                    "API Key 用 Android 密钥库加密后只保存在本机，不会上传，也不会进入备份。截图只会在你提问时发送给所选服务。",
                    fontSize = 12.sp, color = palette.textDim.toComposeColor(), modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        confirmButton = { TextButton(onClick = { adding = true }) { Text("新增服务") } },
        dismissButton = { TextButton(onClick = onClose) { Text("完成") } },
    )
}

/** Signing in with ChatGPT: the way in, the wait for the browser, or the account in use. */
@Composable
private fun ChatGptCard(state: AiPaneState) {
    val palette = LocalPalette.current
    val context = LocalContext.current
    val dim = palette.textDim.toComposeColor()
    LaunchedEffect(Unit) { state.refreshAccount() }
    val account = state.chatgptAccount
    Column(
        Modifier.fillMaxWidth().padding(bottom = 12.dp).clip(RoundedCornerShape(16.dp))
            .background(palette.surface.toComposeColor()).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("ChatGPT 账号", fontSize = 15.sp, fontWeight = FontWeight.Medium)
        when {
            state.signingIn -> {
                Text("请在浏览器里完成登录，完成后回到这里。", fontSize = 13.sp, color = dim)
                TextButton(onClick = { state.cancelSignIn() }) { Text("取消登录") }
            }
            account?.signedIn == true -> {
                Text(
                    (if (account.email.isNotBlank()) "已登录：${account.email}" else "已登录") + "\n提问会使用你的 ChatGPT 套餐额度",
                    fontSize = 13.sp, color = dim,
                )
                Row {
                    TextButton(onClick = {
                        runCatching {
                            context.startActivity(
                                android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(com.nextnotes.ai.ChatGptAuth.USAGE_PAGE)),
                            )
                        }
                    }) { Text("管理用量") }
                    TextButton(onClick = { state.signOutChatGpt() }) { Text("退出登录") }
                }
            }
            else -> {
                Text("用 ChatGPT Plus 或 Pro 套餐的额度提问，不需要 API Key。", fontSize = 13.sp, color = dim)
                Button(onClick = { state.signInChatGpt(context) }, modifier = Modifier.padding(top = 4.dp)) { Text("使用 ChatGPT 登录") }
            }
        }
        state.signInError?.let { Text(it, fontSize = 12.sp, color = palette.danger.toComposeColor()) }
    }
}

/** Which of the account's models the ChatGPT service asks. */
@Composable
private fun ChatGptModelPicker(state: AiPaneState, profile: AiProfile, onDone: () -> Unit) {
    val palette = LocalPalette.current
    LaunchedEffect(Unit) { state.loadChatGptModels() }
    var model by remember { mutableStateOf(profile.model) }
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text("ChatGPT 模型") },
        text = {
            com.nextnotes.ui.KeepBarsHidden()
            Column(Modifier.verticalScroll(rememberScrollState())) {
                val models = state.chatgptModels
                if (models.isEmpty()) Text("正在读取可用的模型…", fontSize = 13.sp, color = palette.textDim.toComposeColor())
                for ((id, name) in models) {
                    Row(Modifier.fillMaxWidth().clickable { model = id }, verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = model == id, onClick = { model = id })
                        Column {
                            Text(name, fontSize = 15.sp)
                            if (name != id) Text(id, fontSize = 12.sp, color = palette.textDim.toComposeColor())
                        }
                    }
                }
                OutlinedTextField(
                    model, { model = it }, label = { Text("模型名称") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                state.signInError?.let { Text(it, fontSize = 12.sp, color = palette.danger.toComposeColor()) }
            }
        },
        confirmButton = {
            TextButton(enabled = model.isNotBlank(), onClick = {
                state.profiles.save(profile.copy(model = model.trim()))
                onDone()
            }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDone) { Text("取消") } },
    )
}

@Composable
private fun ProfileEditor(state: AiPaneState, initial: AiProfile?, onDone: () -> Unit) {
    val palette = LocalPalette.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var protocol by remember { mutableStateOf(initial?.protocol ?: Protocol.OPENAI) }
    var baseUrl by remember { mutableStateOf(initial?.baseUrl ?: "") }
    var model by remember { mutableStateOf(initial?.model ?: "") }
    var opencode by remember { mutableStateOf(initial?.opencodeSession ?: false) }
    var maxTokens by remember { mutableStateOf(initial?.maxTokens?.takeIf { it > 0 }?.toString() ?: "") }
    // An existing key is never shown back; leaving the field empty keeps it.
    var key by remember { mutableStateOf("") }
    var showKey by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }
    var testing by remember { mutableStateOf(false) }
    val hadKey = initial != null && state.keys.has(initial.id)

    fun build() = AiProfile(
        id = initial?.id ?: ProfileStore.newId(),
        name = name.ifBlank { model.ifBlank { "AI 服务" } },
        protocol = protocol,
        baseUrl = baseUrl.trim(),
        model = model.trim(),
        opencodeSession = opencode,
        maxTokens = maxTokens.toIntOrNull()?.takeIf { it > 0 }?.coerceIn(256, 200000) ?: 0,
    )

    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(if (initial == null) "新增 AI 服务" else "编辑 AI 服务") },
        text = {
            com.nextnotes.ui.KeepBarsHidden()
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("从预设填写", fontSize = 12.sp, color = palette.textDim.toComposeColor())
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (preset in AiPresets.all) {
                        FilterChip(
                            selected = false,
                            onClick = {
                                name = preset.name
                                protocol = preset.protocol
                                baseUrl = preset.baseUrl
                                model = preset.model
                                opencode = preset.opencodeSession
                            },
                            label = { Text(preset.name, fontSize = 12.sp) },
                        )
                    }
                }
                OutlinedTextField(name, { name = it }, label = { Text("名称") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("协议", fontSize = 13.sp)
                    FilterChip(protocol == Protocol.OPENAI, { protocol = Protocol.OPENAI }, label = { Text("OpenAI 兼容") })
                    FilterChip(protocol == Protocol.ANTHROPIC, { protocol = Protocol.ANTHROPIC }, label = { Text("Anthropic") })
                }
                OutlinedTextField(
                    baseUrl, { baseUrl = it }, label = { Text("服务地址 Base URL") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), modifier = Modifier.fillMaxWidth(),
                )
                if (baseUrl.trim().startsWith("http://", ignoreCase = true)) {
                    Text(
                        "这是不加密的 HTTP 地址，密钥和截图会明文传输，只建议用于自己局域网里的模型服务。",
                        fontSize = 12.sp, color = palette.danger.toComposeColor(),
                    )
                }
                OutlinedTextField(model, { model = it }, label = { Text("模型名称") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    key, { key = it },
                    label = { Text(if (hadKey) "API Key（留空则保持不变）" else "API Key") },
                    singleLine = true,
                    visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { showKey = !showKey }) {
                            Icon(if (showKey) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, "显示/隐藏")
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    maxTokens, { maxTokens = it.filter(Char::isDigit) }, label = { Text("最大输出 tokens") }, placeholder = { Text("留空：用模型的默认值") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { opencode = !opencode }) {
                    Checkbox(opencode, { opencode = it })
                    Text("发送 x-opencode-session 头（OpenCode Go 需要）", fontSize = 13.sp)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
                        enabled = !testing,
                        onClick = {
                            testing = true
                            testResult = "测试中…"
                            val profile = build()
                            val k = key.ifEmpty { initial?.let { state.keys.get(it.id) }.orEmpty() }
                            scope.launch {
                                testResult = state.test(profile, k)
                                testing = false
                            }
                        },
                    ) { Text("测试连接") }
                    Spacer(Modifier.width(4.dp))
                    testResult?.let { Text(it, fontSize = 12.sp, modifier = Modifier.weight(1f)) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val profile = build()
                state.profiles.save(profile)
                if (key.isNotEmpty()) state.keys.put(profile.id, key.trim())
                if (initial == null) state.profiles.setActive(profile.id)
                onDone()
            }, enabled = baseUrl.isNotBlank() && model.isNotBlank()) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDone) { Text("取消") } },
    )
}
