package com.kusa.weblauncher2

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
import androidx.compose.ui.unit.dp
import com.kusa.weblauncher2.data.SlotInfo

@Composable
fun EditSlotDialog(
    slotKey: String,
    initialInfo: SlotInfo?,          // ★バグ③修正: null許容にする
    onDismiss: () -> Unit,
    onSave: (SlotInfo) -> Unit
) {
    // ★バグ③修正: initialInfo が null（Flow未取得）の間はローディング表示にして
    //              remember の確定を initialInfo が届いてから行う
    if (initialInfo == null) {
        // DataStoreからの読み込み待ち中は何も表示しない（すぐ届く）
        return
    }

    var label    by remember(slotKey) { mutableStateOf(initialInfo.label) }
    var url      by remember(slotKey) { mutableStateOf(initialInfo.url) }
    var iconColor by remember(slotKey) { mutableStateOf(initialInfo.iconColor.ifEmpty { "#808080" }) }
    var isSublink by remember(slotKey) { mutableStateOf(initialInfo.isSublink) }

    val colorPalette = listOf(
        "#F44336", "#E91E63", "#9C27B0", "#673AB7",
        "#3F51B5", "#2196F3", "#03A9F4", "#00BCD4",
        "#009688", "#4CAF50", "#8BC34A", "#CDDC39",
        "#FFEB3B", "#FFC107", "#FF9800", "#FF5722"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("スロットの設定") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                TextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("表示名") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { isSublink = !isSublink }
                ) {
                    Checkbox(checked = isSublink, onCheckedChange = { isSublink = it })
                    Text("下の階層（サブ画面）へのリンクにする")
                }

                if (!isSublink) {
                    TextField(
                        value = url,
                        onValueChange = { url = it },
                        label = { Text("URL") },
                        placeholder = { Text("https://...") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Text("アイコンの色", style = MaterialTheme.typography.labelMedium)
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    modifier = Modifier.height(160.dp),
                    contentPadding = PaddingValues(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(colorPalette) { colorHex ->
                        val isSelected = iconColor.uppercase() == colorHex.uppercase()
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
                                .clickable { iconColor = colorHex }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val newInfo = initialInfo.copy(
                        label = label,
                        url = if (isSublink) "" else url,
                        iconColor = iconColor,
                        isSublink = isSublink
                    )
                    onSave(newInfo)
                    onDismiss()
                },
                enabled = label.isNotEmpty()
            ) {
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
