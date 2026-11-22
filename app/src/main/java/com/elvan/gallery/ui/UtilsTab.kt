package com.elvan.gallery.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import com.composables.icons.materialsymbols.MaterialSymbols as Sym
import com.composables.icons.materialsymbols.rounded.*
import com.elvan.navil.ElvanLazyColumn
import com.elvan.navil.ElvanSettingsRow
import com.elvan.navil.ElvanSettingsSection

private val quoteColors = listOf(
    0xFF000000, 0xFFFFFFFF, 0xFF1E7BF2, 0xFF16A864, 0xFFE8930C, 0xFF8A5CE6,
    0xFFE53935, 0xFF00897B, 0xFF5D4037, 0xFF37474F, 0xFFFFD54F, 0xFFF48FB1
)

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
/** Picks the background colour of a quote image, then makes it. */
@androidx.compose.runtime.Composable
fun QuoteColorDialog(onDismiss: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val colors = com.elvan.navil.rememberShellColors()
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.floatingBg,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
        title = { androidx.compose.material3.Text("Quote colour", color = colors.textPrimary) },
        text = {
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)
            ) {
                quoteColors.forEach { c ->
                    androidx.compose.foundation.layout.Box(
                        Modifier.size(44.dp)
                            .then(Modifier.clip(androidx.compose.foundation.shape.CircleShape))
                            .background(androidx.compose.ui.graphics.Color(c))
                            .border(1.dp, colors.floatingBorder, androidx.compose.foundation.shape.CircleShape)
                            .clickable {
                                val saved = saveEdited(context, com.elvan.gallery.data.quoteCard(context, c.toInt()), "Quotes", "Quote")
                                android.widget.Toast.makeText(context, if (saved) "Saved to Pictures/Quotes" else "Could not save", android.widget.Toast.LENGTH_SHORT).show()
                                onDismiss()
                            }
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { androidx.compose.material3.Text("Cancel", color = colors.textSecondary) } }
    )
}

