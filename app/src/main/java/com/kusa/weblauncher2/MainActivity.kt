package com.kusa.weblauncher2

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import com.kusa.weblauncher2.data.SlotInfo
import com.kusa.weblauncher2.data.SlotRepository
import com.kusa.weblauncher2.ui.theme.TeaBackground
import com.kusa.weblauncher2.ui.theme.TeaToolbar
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
                    color = TeaBackground
                ) {
                    SlotManagerScreen(
                        repository = repository,
                        initialSlot = initialSlot,
                        onLaunchUrl = { info -> launchWithCustomTabs(info, shouldFinish = false) }
                    )
                }
            }
        }
    }
}

fun depthOf(parentId: String): Int =
    if (parentId == "root") 0 else parentId.count { it == '_' }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SlotManagerScreen(
    repository: SlotRepository,
    initialSlot: String?,
    onLaunchUrl: (SlotInfo) -> Unit
) {
    val scope = rememberCoroutineScope()
    var currentParentId by remember { mutableStateOf(initialSlot ?: "root") }
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
                        Text(stringResource(R.string.app_name), fontWeight = FontWeight.Bold)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = TeaBackground
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(TeaBackground)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .background(TeaToolbar)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "🍵",
                    fontSize = 32.sp,
                    modifier = Modifier.clickable { currentParentId = "root" }
                )
                if (currentParentId != "root") {
                    val breadcrumbKeys = remember(currentParentId) {
                        buildList {
                            var path = "root"
                            currentParentId.split("_").drop(1).forEach { part ->
                                path = "${path}_$part"
                                add(path)
                            }
                        }
                    }
                    breadcrumbKeys.forEach { key ->
                        Text(" ＞ ", fontSize = 20.sp, color = androidx.compose.ui.graphics.Color.Gray)
                        BreadcrumbLabel(
                            slotKey = key,
                            repository = repository,
                            isLast = key == currentParentId,
                            onClick = { currentParentId = key }
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    TextButton(onClick = {
                        currentParentId = currentParentId.substringBeforeLast("_", "root")
                    }) {
                        Text(stringResource(R.string.back), fontSize = 18.sp)
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
            },
            onDelete = {
                scope.launch {
                    repository.deleteSlotRecursive(slotKey)
                    selectedSlot = null
                }
            }
        )
    }
}

@Composable
private fun BreadcrumbLabel(
    slotKey: String,
    repository: SlotRepository,
    isLast: Boolean,
    onClick: () -> Unit
) {
    val info by repository.getSlotInfo(slotKey).collectAsState(initial = SlotInfo())
    Text(
        text = info.label.ifEmpty { "?" },
        fontSize = 20.sp,
        fontWeight = if (isLast) FontWeight.Bold else FontWeight.Normal,
        modifier = if (!isLast) Modifier.clickable { onClick() } else Modifier
    )
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
                            android.graphics.Color.parseColor(info?.iconColor ?: "#8B4513")
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
                    text = if (isRegistered) info!!.label else "",
                    style = MaterialTheme.typography.titleMedium
                )
                when {
                    isRegistered && info?.isSublink == true ->
                        Text(stringResource(R.string.sub_screen_indicator), style = MaterialTheme.typography.bodySmall,
                            color = androidx.compose.ui.graphics.Color.Gray)
                    isRegistered && info?.url?.isNotEmpty() == true ->
                        Text(info.url, style = MaterialTheme.typography.bodySmall,
                            color = androidx.compose.ui.graphics.Color.Gray)
                }
            }
        }
    }
}
