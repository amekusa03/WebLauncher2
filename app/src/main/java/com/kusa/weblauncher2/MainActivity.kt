package com.kusa.weblauncher2

import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.graphics.Color
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.FontWeight
import com.kusa.weblauncher2.data.SlotInfo
import com.kusa.weblauncher2.data.SlotRepository
import com.kusa.weblauncher2.ui.theme.WebLauncher2Theme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val repository by lazy { SlotRepository(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val initialSlot = intent.getStringExtra("TARGET_SLOT")

        enableEdgeToEdge()
        setContent {
            WebLauncher2Theme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SlotManagerScreen(
                        repository = repository,
                        initialSlot = initialSlot,
                        onUpdateAlias = { slotKey, enabled ->
                            updateAliasEnabled(slotKey, enabled)
                        }
                    )
                }
            }
        }
    }

    private fun updateAliasEnabled(slotKey: String, enabled: Boolean) {
        val aliasName = slotKey.replace("slot_", "Slot").replaceFirstChar { it.uppercase() }
        val componentName = ComponentName(packageName, "$packageName.$aliasName")

        val newState = if (enabled) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }

        try {
            packageManager.setComponentEnabledSetting(
                componentName,
                newState,
                0 
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

fun formatSlotDisplayName(slotKey: String): String {
    return when (slotKey) {
        "slot_1" -> "Web①"
        "slot_2" -> "Web②"
        "slot_3" -> "Web③"
        "slot_4" -> "Web④"
        "slot_5" -> "Web⑤"
        else -> slotKey
    }
}

// ... 既存のimportは維持 ...
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SlotManagerScreen(
    repository: SlotRepository,
    initialSlot: String?,
    onUpdateAlias: (String, Boolean) -> Unit
) {
    val scope = rememberCoroutineScope()
    // 階層管理用の状態
    var currentParentId by remember { mutableStateOf("root") }
    val slots = listOf("1", "2", "3", "4", "5") // 各階層に5つの枠
    var selectedSlot by remember { mutableStateOf<String?>(null) }

    // TODO: repository.getSlotsByParent(currentParentId) のような形式でデータを取得するように変更する

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🍵", fontSize = 24.sp) // お湯呑み
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("あわ茶", fontWeight = FontWeight.Bold)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = androidx.compose.ui.graphics.Color(0xFFF5F5F0)
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(androidx.compose.ui.graphics.Color(0xFFF5F5F0))
        ) {
            // --- パンくずリスト（階層表示）エリア ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // ルート（あわ茶）アイコン
                Text("🍵", modifier = Modifier.clickable { currentParentId = "root" })

                // 階層がある場合にアイコンを並べる（モック）
                if (currentParentId != "root") {
                    Text(" ＞ ")
                    // ここに親階層のアイコンを並べる
                    Text("📄 現在の階層名", style = MaterialTheme.typography.bodyMedium)
                }
            }

            // --- 5つのスロット表示 ---
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(slots) { index ->
                    // 枠のID（例: root_1, sub1_2 など）
                    val slotKey = "${currentParentId}_$index"
                    val info by repository.getSlotInfo(slotKey).collectAsState(initial = SlotInfo())

                    SlotItemRow(
                        info = info,
                        index = index.toInt(),
                        onClick = {
                            if (info?.url?.isNotEmpty() == true) {
                                // URL起動処理
                            } else if (info?.IsSublink == true) {
                                // サブ画面へ移動
                                // currentParentId = slotKey
                            } else {
                                // 未登録：編集画面へ
                                selectedSlot = slotKey
                            }
                        },
                        onLongClick = { selectedSlot = slotKey }
                    )
                }
            }
        }
    }

    // 編集ダイアログ（既存のEditSlotDialogを流用、ただし「種別」の選択肢を追加が必要）
    if (selectedSlot != null) {
        val slotKey = selectedSlot!!
        val currentInfo by repository.getSlotInfo(slotKey).collectAsState(initial = null)
        EditSlotDialog(
            slotKey = slotKey,
            initialInfo = currentInfo ?: SlotInfo(),
            onDismiss = { selectedSlot = null },
            onSave = { newInfo ->
                scope.launch {
                    repository.saveSlotInfo(slotKey, newInfo)
                    selectedSlot = null
                }
            }
        )
    }
}

@Composable
fun SlotItemRow(
    info: SlotInfo?,
    index: Int,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val isRegistered = info?.label?.isNotEmpty() == true

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp)
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        color = androidx.compose.ui.graphics.Color.White,
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // アイコン部分（16色 or ＋）
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        if (isRegistered) {
                            val colorInt = try {
                            //    Color.parseColor(info?.iconColor)
                                android.graphics.Color.parseColor(info?.iconColor ?: "#808080")
                            } catch (e: Exception) {
                                //Color.GRAY
                                android.graphics.Color.GRAY
                            }
                            androidx.compose.ui.graphics.Color(colorInt)
                        } else {
                            androidx.compose.ui.graphics.Color.LightGray
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isRegistered) {
                    Text(
                        info?.label?.take(1) ?: "",
                        color = androidx.compose.ui.graphics.Color.White
                    )
                } else {
                    //Icon(Icons.Default.Add, contentDescription = null)
                    Icon(imageVector = Icons.Default.Add, contentDescription = null)
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column {
                Text(
                    text = if (isRegistered) info!!.label else "未登録 (${index + 1})",
                    style = MaterialTheme.typography.titleMedium
                )
                if (isRegistered && info?.url?.isNotEmpty() == true) {
                    Text(
                        info.url,
                        style = MaterialTheme.typography.bodySmall,
                        color = androidx.compose.ui.graphics.Color.Gray
                    )
                }
            }
        }
    }
}

@Composable
fun EditSlotDialog(
    slotKey: String,
    initialInfo: SlotInfo,
    onDismiss: () -> Unit,
    onSave: (SlotInfo) -> Unit
) {
    var label by remember { mutableStateOf(initialInfo.label) }
    var url by remember { mutableStateOf(initialInfo.url) }
    var iconColor by remember { mutableStateOf(initialInfo.iconColor.ifEmpty { "#808080" }) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("スロット編集") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("名前 (ラベル)") },
                    singleLine = true
                )
                TextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("URL") },
                    singleLine = true
                )
                Text("アイコン色 (HEX):", style = MaterialTheme.typography.bodySmall)
                TextField(
                    value = iconColor,
                    onValueChange = { iconColor = it },
                    placeholder = { Text("#RRGGBB") }
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                onSave(initialInfo.copy(label = label, url = url, iconColor = iconColor))
            }) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("キャンセル")
            }
        }
    )
}