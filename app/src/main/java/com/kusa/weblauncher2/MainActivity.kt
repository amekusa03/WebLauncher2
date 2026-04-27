package com.kusa.weblauncher2

import android.content.ComponentName
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import com.kusa.weblauncher2.data.SlotInfo
import com.kusa.weblauncher2.data.SlotRepository
import com.kusa.weblauncher2.ui.theme.WebLauncher2Theme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

    private fun launchBrowser(info: SlotInfo) {
        try {
            val colorInt = try {
                android.graphics.Color.parseColor(info.iconColor)
            } catch (e: Exception) { android.graphics.Color.BLUE }
            val colorParams = CustomTabColorSchemeParams.Builder().setToolbarColor(colorInt).build()
            val customTabsIntent = CustomTabsIntent.Builder()
                .setDefaultColorSchemeParams(colorParams)
                .setShowTitle(true)
                .build()
            customTabsIntent.launchUrl(this, Uri.parse(info.url))
            finish()
        } catch (e: Exception) { e.printStackTrace() }
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

fun depthOf(parentId: String): Int =
    if (parentId == "root") 0 else parentId.count { it == '_' }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SlotManagerScreen(
    repository: SlotRepository,
    initialSlot: String?,
    onLaunchUrl: (SlotInfo) -> Unit,
    onUpdateAlias: (String, Boolean) -> Unit
) {
    val scope = rememberCoroutineScope()

    // ★チューニング3: rememberSaveable ではなく remember を使い、
    //   かつ Activity の FLAG_ACTIVITY_CLEAR_TOP と合わせることで
    //   アプリ再起動（タスク再生成）時は常に "root" から始まる
    var currentParentId by remember { mutableStateOf("root") }

    val slotIndices = listOf(0, 1, 2, 3, 4)
    var selectedSlot by remember { mutableStateOf<String?>(null) }
    val currentDepth = depthOf(currentParentId)

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
            // ★チューニング2: パンくず縦横2倍（高さ64dp・文字32sp）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .background(androidx.compose.ui.graphics.Color(0xFFEAEAE0))
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "🍵",
                    fontSize = 32.sp,
                    modifier = Modifier.clickable { currentParentId = "root" }
                )
                if (currentParentId != "root") {
                    var buildPath = "root"
                    currentParentId.split("_").drop(1).forEach { part ->
                        buildPath = "${buildPath}_$part"
                        val pathSnapshot = buildPath
                        val isLast = (pathSnapshot == currentParentId)
                        Text(" ＞ ", fontSize = 20.sp, color = androidx.compose.ui.graphics.Color.Gray)
                        Text(
                            text = part,
                            fontSize = 20.sp,
                            fontWeight = if (isLast) FontWeight.Bold else FontWeight.Normal,
                            modifier = if (!isLast) Modifier.clickable { currentParentId = pathSnapshot }
                                       else Modifier
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    TextButton(onClick = {
                        currentParentId = currentParentId.substringBeforeLast("_", "root")
                    }) {
                        Text("←戻る", fontSize = 18.sp)
                    }
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
                        repository = repository,
                        onClick = {
                            when {
                                // ★不具合1: 4階層制限
                                info.isSublink -> if (currentDepth < 4) currentParentId = slotKey
                                info.url.isNotEmpty() -> onLaunchUrl(info)
                                else -> selectedSlot = slotKey
                            }
                        },
                        onLongClick = { selectedSlot = slotKey }
                    )
                }
            }
        }
    }

    if (selectedSlot != null) {
        val slotKey = selectedSlot!!
        val currentInfo by remember(slotKey) {
            repository.getSlotInfo(slotKey)
        }.collectAsState(initial = null)

        EditSlotDialog(
            slotKey = slotKey,
            initialInfo = currentInfo,
            repository = repository,
            currentDepth = currentDepth + 1,
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
    repository: SlotRepository,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val isRegistered = info?.label?.isNotEmpty() == true

    var iconBitmap by remember(info?.iconBase64) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(info?.iconBase64) {
        val b64 = info?.iconBase64 ?: ""
        iconBitmap = if (b64.isNotEmpty()) {
            withContext(Dispatchers.IO) { repository.base64ToBitmap(b64) }
        } else null
    }

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
                    .background(androidx.compose.ui.graphics.Color.LightGray),
                contentAlignment = Alignment.Center
            ) {
                when {
                    iconBitmap != null ->
                        Image(
                            bitmap = iconBitmap!!.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.size(48.dp).clip(CircleShape)
                        )
                    !isRegistered ->
                        Icon(imageVector = Icons.Default.Add, contentDescription = null)
                    else -> {
                        val colorInt = try {
                            android.graphics.Color.parseColor(info?.iconColor ?: "#808080")
                        } catch (e: Exception) { android.graphics.Color.GRAY }
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(androidx.compose.ui.graphics.Color(colorInt)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(info?.label?.take(1) ?: "",
                                color = androidx.compose.ui.graphics.Color.White)
                        }
                    }
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
