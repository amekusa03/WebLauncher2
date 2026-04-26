package com.kusa.weblauncher2

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
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
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.shape.RoundedCornerShape
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
                        onLaunchUrl = { info -> launchBrowser(info) },
                        onUpdateAlias = { slotKey, enabled -> updateAliasEnabled(slotKey, enabled) }
                    )
                }
            }
        }
    }

    // ★URL起動はActivityのコンテキストで行う
    private fun launchBrowser(info: SlotInfo) {
        try {
            val colorInt = try {
                android.graphics.Color.parseColor(info.iconColor)
            } catch (e: Exception) { android.graphics.Color.BLUE }

            val colorParams = CustomTabColorSchemeParams.Builder()
                .setToolbarColor(colorInt)
                .build()
            val customTabsIntent = CustomTabsIntent.Builder()
                .setDefaultColorSchemeParams(colorParams)
                .setShowTitle(true)
                .build()
            customTabsIntent.launchUrl(this, Uri.parse(info.url))
            // 仕様：ブラウザ起動後にアプリを終了
            finish()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun updateAliasEnabled(slotKey: String, enabled: Boolean) {
        val aliasName = slotKey.replace("slot_", "Slot").replaceFirstChar { it.uppercase() }
        val componentName = ComponentName(packageName, "$packageName.$aliasName")
        val newState = if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                       else        PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        try {
            packageManager.setComponentEnabledSetting(componentName, newState, 0)
        } catch (e: Exception) { e.printStackTrace() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SlotManagerScreen(
    repository: SlotRepository,
    initialSlot: String?,
    onLaunchUrl: (SlotInfo) -> Unit,
    onUpdateAlias: (String, Boolean) -> Unit
) {
    val scope = rememberCoroutineScope()
    var currentParentId by remember { mutableStateOf("root") }
    val slotIndices = listOf(0, 1, 2, 3, 4)
    var selectedSlot by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🍵", fontSize = 24.sp)
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
            // パンくずリスト
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "🍵",
                    modifier = Modifier.clickable { currentParentId = "root" },
                    fontWeight = if (currentParentId == "root") FontWeight.Bold else FontWeight.Normal
                )
                if (currentParentId != "root") {
                    Text(" ＞ ")
                    Text(
                        text = "階層: $currentParentId",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    TextButton(onClick = {
                        val parent = currentParentId.substringBeforeLast("_", "root")
                        currentParentId = parent
                    }) { Text("戻る") }
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(slotIndices) { index ->
                    val slotKey = "${currentParentId}_$index"
                    val info by remember(slotKey) {
                        repository.getSlotInfo(slotKey)
                    }.collectAsState(initial = SlotInfo())

                    SlotItemRow(
                        info = info,
                        index = index,
                        onClick = {
                            when {
                                // サブリンク → 階層を下りる
                                info.isSublink -> {
                                    currentParentId = slotKey
                                }
                                // ★URL登録済み → ブラウザで開く（仕様通り）
                                info.url.isNotEmpty() -> {
                                    onLaunchUrl(info)
                                }
                                // 未登録 → 編集画面
                                else -> {
                                    selectedSlot = slotKey
                                }
                            }
                        },
                        // 長押し → 常に編集画面
                        onLongClick = { selectedSlot = slotKey }
                    )
                }
            }
        }
    }

    // 編集ダイアログ
    if (selectedSlot != null) {
        val slotKey = selectedSlot!!
        val currentInfo by remember(slotKey) {
            repository.getSlotInfo(slotKey)
        }.collectAsState(initial = null)

        EditSlotDialog(
            slotKey = slotKey,
            initialInfo = currentInfo,
            onDismiss = { selectedSlot = null },
            onSave = { updateInfo ->
                scope.launch {
                    repository.saveSlotInfo(slotKey, updateInfo)
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
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        color = androidx.compose.ui.graphics.Color.White,
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        if (isRegistered) {
                            val colorInt = try {
                                android.graphics.Color.parseColor(info?.iconColor ?: "#808080")
                            } catch (e: Exception) { android.graphics.Color.GRAY }
                            androidx.compose.ui.graphics.Color(colorInt)
                        } else {
                            androidx.compose.ui.graphics.Color.LightGray
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isRegistered) {
                    Text(info?.label?.take(1) ?: "", color = androidx.compose.ui.graphics.Color.White)
                } else {
                    Icon(imageVector = Icons.Default.Add, contentDescription = null)
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column {
                Text(
                    text = if (isRegistered) info!!.label else "未登録 (${index + 1})",
                    style = MaterialTheme.typography.titleMedium
                )
                when {
                    isRegistered && info?.isSublink == true ->
                        Text("📁 サブ画面", style = MaterialTheme.typography.bodySmall,
                            color = androidx.compose.ui.graphics.Color.Gray)
                    isRegistered && info?.url?.isNotEmpty() == true ->
                        Text(info.url, style = MaterialTheme.typography.bodySmall,
                            color = androidx.compose.ui.graphics.Color.Gray)
                }
            }
        }
    }
}
