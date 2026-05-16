package com.kusa.weblauncher2

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.util.Patterns
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kusa.weblauncher2.data.IconType
import com.kusa.weblauncher2.data.SlotInfo
import com.kusa.weblauncher2.data.SlotRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.URL

private val colorPalette = listOf(
    "#8B4513", "#5D4037", "#F44336", "#E91E63",
    "#9C27B0", "#673AB7", "#3F51B5", "#2196F3",
    "#03A9F4", "#00BCD4", "#009688", "#4CAF50",
    "#8BC34A", "#CDDC39", "#FFEB3B", "#FFC107"
)

private fun makeColorIconBitmap(label: String, colorHex: String): Bitmap {
    val size = 144
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.color = try { android.graphics.Color.parseColor(colorHex) } catch (e: Exception) { android.graphics.Color.GRAY }
    canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
    paint.color = android.graphics.Color.WHITE
    paint.textSize = size * 0.5f
    paint.typeface = Typeface.DEFAULT_BOLD
    paint.textAlign = Paint.Align.CENTER
    val ch = label.take(1).ifEmpty { "?" }
    val y = size / 2f - (paint.descent() + paint.ascent()) / 2f
    canvas.drawText(ch, size / 2f, y, paint)
    return bitmap
}

private suspend fun fetchFavicon(pageUrl: String): Bitmap? = withContext(Dispatchers.IO) {
    withTimeoutOrNull(10_000L) {
        try {
            val host = URL(pageUrl).host
            val faviconUrl = "https://www.google.com/s2/favicons?sz=128&domain=$host"
            val stream = URL(faviconUrl).openStream()
            android.graphics.BitmapFactory.decodeStream(stream)
        } catch (e: Exception) { null }
    }
}

@Composable
fun EditSlotDialog(
    slotKey: String,
    initialInfo: SlotInfo?,
    repository: SlotRepository,
    currentDepth: Int,
    onDismiss: () -> Unit,
    onSave: (SlotInfo) -> Unit,
    onDelete: () -> Unit
) {
    if (initialInfo == null) return

    val scope = rememberCoroutineScope()

    var label     by remember(slotKey) { mutableStateOf(initialInfo.label) }
    var url       by remember(slotKey) { mutableStateOf(initialInfo.url) }
    var isSublink by remember(slotKey) { mutableStateOf(initialInfo.isSublink) }
    var iconColor by remember(slotKey) { mutableStateOf(initialInfo.iconColor.ifEmpty { "#8B4513" }) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    var selectedIconType  by remember(slotKey) { mutableStateOf<IconType?>(
        if (initialInfo.iconBase64.isEmpty()) null else initialInfo.iconType
    )}
    var previewBitmap     by remember(slotKey) { mutableStateOf<Bitmap?>(null) }
    var isFetchingFavicon by remember { mutableStateOf(false) }

    val parentId = remember(slotKey) { slotKey.substringBeforeLast("_") }
    val siblings by remember(parentId) {
        repository.getSlotsUnder(parentId)
    }.collectAsState(initial = emptyList())

    val urlError = remember(url, isSublink) {
        if (!isSublink && url.isNotEmpty() && !Patterns.WEB_URL.matcher(url).matches()) {
            "URLの形式が正しくありません"
        } else if (!isSublink && url.isNotEmpty() &&
            !url.startsWith("http://") && !url.startsWith("https://")) {
            "http:// または https:// で始まる必要があります"
        } else null
    }

    val isDuplicateName = remember(label, siblings) {
        label.trim().isNotEmpty() &&
        siblings.any { (key, info) -> key != slotKey && info.label.trim() == label.trim() }
    }

    LaunchedEffect(slotKey) {
        if (initialInfo.iconBase64.isNotEmpty()) {
            previewBitmap = withContext(Dispatchers.IO) {
                repository.base64ToBitmap(initialInfo.iconBase64)
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.dialog_title_delete)) },
            text = { Text(stringResource(R.string.dialog_message_delete)) },
            confirmButton = {
                Button(
                    onClick = { onDelete() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFD32F2F)
                    )
                ) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_title_settings)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                TextField(
                    value = label,
                    onValueChange = { if (it.length <= 64) label = it },
                    label = { Text(stringResource(R.string.label_display_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    isError = isDuplicateName
                )
                if (isDuplicateName) {
                    Text(
                        stringResource(R.string.error_duplicate_name),
                        color = Color.Red,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                if (currentDepth < 4) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { isSublink = !isSublink }
                    ) {
                        Checkbox(checked = isSublink, onCheckedChange = { isSublink = it })
                        Text(stringResource(R.string.checkbox_sublink))
                    }
                } else {
                    Text(
                        stringResource(R.string.error_max_depth),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }

                TextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text(stringResource(if (isSublink) R.string.label_favicon_url else R.string.label_url)) },
                    placeholder = { Text("https://...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    isError = urlError != null
                )
                if (urlError != null) {
                    Text(
                        urlError,
                        color = Color.Red,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Text(stringResource(R.string.label_icon), style = MaterialTheme.typography.labelMedium)

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color.LightGray)
                            .border(
                                width = if (selectedIconType == IconType.FAVICON) 3.dp else 0.dp,
                                color = if (selectedIconType == IconType.FAVICON) Color.Black else Color.Transparent,
                                shape = CircleShape
                            )
                            .clickable {
                                val targetUrl = url.trim()
                                if (targetUrl.startsWith("http://") || targetUrl.startsWith("https://")) {
                                    isFetchingFavicon = true
                                    scope.launch {
                                        val bmp = fetchFavicon(targetUrl)
                                        if (bmp != null) {
                                            previewBitmap = bmp
                                            selectedIconType = IconType.FAVICON
                                        } else {
                                            selectedIconType = null
                                            previewBitmap = null
                                        }
                                        isFetchingFavicon = false
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        when {
                            isFetchingFavicon ->
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                            selectedIconType == IconType.FAVICON && previewBitmap != null ->
                                Image(
                                    bitmap = previewBitmap!!.asImageBitmap(),
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp).clip(CircleShape)
                                )
                            else ->
                                Text(stringResource(R.string.icon_auto), fontSize = 11.sp, color = Color.DarkGray)
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.icon_auto_desc), style = MaterialTheme.typography.bodySmall)
                }

                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    modifier = Modifier.height(160.dp),
                    contentPadding = PaddingValues(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(colorPalette) { colorHex ->
                        val isSelected = selectedIconType == IconType.COLOR &&
                                iconColor.uppercase() == colorHex.uppercase()
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color(android.graphics.Color.parseColor(colorHex)))
                                .border(
                                    width = if (isSelected) 3.dp else 0.dp,
                                    color = if (isSelected) Color.Black else Color.Transparent,
                                    shape = CircleShape
                                )
                                .clickable {
                                    iconColor = colorHex
                                    selectedIconType = IconType.COLOR
                                    previewBitmap = makeColorIconBitmap(label.ifEmpty { "?" }, colorHex)
                                }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    scope.launch {
                        val (finalIconType, finalBase64) = when (selectedIconType) {
                            IconType.FAVICON -> {
                                val bmp = previewBitmap
                                if (bmp != null) IconType.FAVICON to repository.bitmapToBase64(bmp)
                                else IconType.COLOR to ""
                            }
                            IconType.COLOR -> {
                                val bmp = previewBitmap ?: makeColorIconBitmap(label.ifEmpty { "?" }, iconColor)
                                IconType.COLOR to repository.bitmapToBase64(bmp)
                            }
                            else -> IconType.COLOR to ""
                        }

                        onSave(
                            SlotInfo(
                                label = label.trim(),
                                url = url.trim(),
                                isSublink = isSublink,
                                iconColor = iconColor,
                                iconType = finalIconType,
                                iconBase64 = finalBase64
                            )
                        )
                    }
                },
                enabled = label.isNotEmpty() && !isDuplicateName && urlError == null
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            Row {
                if (initialInfo.label.isNotEmpty()) {
                    TextButton(
                        onClick = { showDeleteConfirm = true },
                        colors = ButtonDefaults.textButtonColors(contentColor = Color.Red)
                    ) {
                        Text(stringResource(R.string.action_delete))
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        }
    )
}
