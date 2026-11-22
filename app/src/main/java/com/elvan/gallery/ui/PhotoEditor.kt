package com.elvan.gallery.ui

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.materialsymbols.MaterialSymbols as Sym
import com.composables.icons.materialsymbols.rounded.*
import com.elvan.navil.MaterialSymbols
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

// ── The colour maths (tested in ColorMatrixTest) ─────────────────────────────────────────────────────────────────────

/** A look: a 4x5 colour matrix (Android's layout). */
class PhotoFilter(val name: String, val matrix: FloatArray)

private fun identity() = floatArrayOf(1f, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 1f, 0f)

private fun saturationMatrix(s: Float): FloatArray {
    val r = 0.213f * (1 - s); val g = 0.715f * (1 - s); val b = 0.072f * (1 - s)
    return floatArrayOf(r + s, g, b, 0f, 0f, r, g + s, b, 0f, 0f, r, g, b + s, 0f, 0f, 0f, 0f, 0f, 1f, 0f)
}

private fun scaleOffset(sr: Float, sg: Float, sb: Float, o: Float = 0f, og: Float = o, ob: Float = o) =
    floatArrayOf(sr, 0f, 0f, 0f, o, 0f, sg, 0f, 0f, og, 0f, 0f, sb, 0f, ob, 0f, 0f, 0f, 1f, 0f)

/** a then b, both 4x5 colour matrices. */
fun concat(b: FloatArray, a: FloatArray): FloatArray {
    val out = FloatArray(20)
    for (row in 0 until 4) for (col in 0 until 5) {
        var v = if (col == 4) b[row * 5 + 4] else 0f
        for (k in 0 until 4) v += b[row * 5 + k] * a[k * 5 + col]
        out[row * 5 + col] = v
    }
    return out
}

val photoFilters = listOf(
    PhotoFilter("Original", identity()),
    PhotoFilter("Vivid", saturationMatrix(1.45f)),
    PhotoFilter("Warm", scaleOffset(1.08f, 1.02f, 0.9f, 12f, 4f, -8f)),
    PhotoFilter("Cool", scaleOffset(0.92f, 1f, 1.1f, -6f, 2f, 14f)),
    PhotoFilter("Mono", saturationMatrix(0f)),
    PhotoFilter("Sepia", floatArrayOf(0.393f, 0.769f, 0.189f, 0f, 0f, 0.349f, 0.686f, 0.168f, 0f, 0f, 0.272f, 0.534f, 0.131f, 0f, 0f, 0f, 0f, 0f, 1f, 0f)),
    PhotoFilter("Fade", concat(saturationMatrix(0.7f), scaleOffset(0.85f, 0.85f, 0.85f, 28f))),
    PhotoFilter("Noir", concat(scaleOffset(1.35f, 1.35f, 1.35f, -30f), saturationMatrix(0f))),
    PhotoFilter("Pulse", concat(scaleOffset(1.05f, 0.98f, 1.12f, 4f, -2f, 10f), saturationMatrix(1.25f))),
    PhotoFilter("Dusk", concat(scaleOffset(1.1f, 0.95f, 0.85f, 10f, -4f, -10f), saturationMatrix(0.85f)))
)

/** Brightness (-1..1), contrast (-1..1), saturation (-1..1), warmth (-1..1) on top of a filter. */
fun adjustMatrix(filter: FloatArray, brightness: Float, contrast: Float, saturation: Float, warmth: Float): FloatArray {
    val c = 1f + contrast
    val t = 128f * (1 - c) + brightness * 80f
    val bc = scaleOffset(c, c, c, t)
    val w = scaleOffset(1f, 1f, 1f, warmth * 25f, warmth * 6f, -warmth * 25f)
    return concat(w, concat(saturationMatrix(1f + saturation), concat(bc, filter)))
}

// ── The edit, as one value (undo / redo keep a list of these) ────────────────────────────────────────────────────────

/** The Tone controls that a colour matrix (plus a vignette) can do. Values are -100..100. */
enum class Tone(val label: String, val icon: ImageVector) {
    LightBalance("Light balance", Sym.Rounded.Lightbulb),
    Brightness("Brightness", Sym.Rounded.Light_mode),
    Exposure("Exposure", Sym.Rounded.Exposure),
    Contrast("Contrast", Sym.Rounded.Contrast),
    Saturation("Saturation", Sym.Rounded.Palette),
    Temperature("Temperature", Sym.Rounded.Thermostat),
    Tint("Tint", Sym.Rounded.Colorize),
    Vignette("Vignette", Sym.Rounded.Vignette)
}

data class DrawStroke(val points: List<Offset>, val color: Int, val width: Float, val alpha: Float)
data class TextItem(val text: String, val at: Offset, val color: Int, val size: Float)

data class EditState(
    val filter: Int = 0,
    val tones: Map<Tone, Float> = emptyMap(),
    val rotation: Int = 0,
    val flip: Boolean = false,
    val straighten: Float = 0f,
    val crop: Rect = Rect(0f, 0f, 1f, 1f),
    val aspect: Float? = null,
    val strokes: List<DrawStroke> = emptyList(),
    val texts: List<TextItem> = emptyList()
) {
    fun tone(t: Tone) = (tones[t] ?: 0f) / 100f

    /** Every colour change of the edit, as one matrix. */
    fun matrix(): FloatArray {
        val lb = tone(Tone.LightBalance)
        val exposure = Math.pow(2.0, tone(Tone.Exposure).toDouble()).toFloat()
        val tint = tone(Tone.Tint)
        var m = adjustMatrix(
            photoFilters[filter].matrix,
            brightness = tone(Tone.Brightness) + lb * 0.3f,
            contrast = tone(Tone.Contrast) * 0.8f + lb * 0.2f,
            saturation = tone(Tone.Saturation),
            warmth = tone(Tone.Temperature)
        )
        m = concat(scaleOffset(exposure, exposure, exposure), m)
        m = concat(scaleOffset(1f, 1f, 1f, tint * 8f, -tint * 20f, tint * 8f), m)
        return m
    }

    /** How much to enlarge a straightened picture so no empty corner shows. */
    fun straightenScale(w: Float, h: Float): Float {
        val a = Math.toRadians(abs(straighten).toDouble())
        val r = maxOf(w / h, h / w)
        return (cos(a) + sin(a) * r).toFloat()
    }
}

private enum class EditorTool(val label: String, val icon: ImageVector) {
    Transform("Transform", Sym.Rounded.Crop_rotate),
    Filters("Filters", Sym.Rounded.Filter_vintage),
    Tone("Tone", Sym.Rounded.Tune),
    Decorations("Decorations", Sym.Rounded.Add_reaction),
    Tools("Tools", Sym.Rounded.Apps)
}

// The editor's palette
private val EditorYellow = Color(0xFFFFD90D)
private val PanelGrey = Color(0xFF1A1A1A)
private val RoundGrey = Color(0xFF202023)
private val Disabled = Color(0xFF7D7D7F)

/**
 * The photo editor, laid out as: undo/redo, Revert, Save along the top; the picture; the tool's panel;
 * five tool icons along the bottom (the chosen one yellow). Saves a new picture in Pictures/Edited; the original stays.
 */
@Composable
fun PhotoEditor(uri: String, onClose: () -> Unit, onSaved: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val source by produceState<Bitmap?>(null, uri) { value = withContext(Dispatchers.IO) { loadForEditing(context, Uri.parse(uri)) } }
    val undo = remember { mutableStateListOf<EditState>() }
    val redo = remember { mutableStateListOf<EditState>() }
    var state by remember { mutableStateOf(EditState()) }
    var live by remember { mutableStateOf(EditState()) } // what is on screen while a finger is still moving
    var tool by remember { mutableStateOf(EditorTool.Transform) }
    var saving by remember { mutableStateOf(false) }
    var leaving by remember { mutableStateOf(false) }
    val changed = state != EditState()

    fun commit(next: EditState) {
        if (next == state) { live = next; return }
        undo.add(state); redo.clear(); state = next; live = next
    }
    fun save() {
        val bmp = source ?: return
        saving = true
        scope.launch {
            val ok = withContext(Dispatchers.IO) { saveEdited(context, render(bmp, state)) }
            saving = false
            Toast.makeText(context, if (ok) "Saved to Pictures/Edited" else "Could not save", Toast.LENGTH_SHORT).show()
            if (ok) { onSaved(); onClose() }
        }
    }
    BackHandler { if (changed) leaving = true else onClose() }

    Column(Modifier.fillMaxSize().background(Color.Black)) {
        // Top bar: undo, redo | Revert, Save, more
        Row(
            Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).height(68.dp).padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BarIcon(Sym.Rounded.Undo, "Undo", undo.isNotEmpty()) { redo.add(state); state = undo.removeAt(undo.lastIndex); live = state }
            Spacer(Modifier.width(8.dp))
            BarIcon(Sym.Rounded.Redo, "Redo", redo.isNotEmpty()) { undo.add(state); state = redo.removeAt(redo.lastIndex); live = state }
            Spacer(Modifier.weight(1f))
            BarText("Revert", changed) { commit(EditState()) }
            BarText(if (saving) "Saving…" else "Save", changed && !saving && source != null) { save() }
            BarIcon(MaterialSymbols.Rounded.MoreVert, "More", true) { if (changed) leaving = true else onClose() }
        }

        // The picture
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
            val bmp = source
            // The transform capsule sits over the bottom of the picture area, so the picture stays big
            val reserve = if (tool == EditorTool.Transform) 64f else 0f
            val boxW = maxWidth.value
            val boxH = maxHeight.value - reserve
            if (bmp != null) Box(Modifier.fillMaxSize().padding(bottom = reserve.dp), contentAlignment = Alignment.Center) {
                PictureArea(bmp, live, tool, boxW, boxH, onLive = { live = it }, onCommit = ::commit)
            }
            if (tool == EditorTool.Transform) Box(Modifier.align(Alignment.BottomCenter)) {
                TransformPanel(live, source, onLive = { live = it }, onCommit = ::commit)
            }
        }

        // The tool's panel
        AnimatedContent(tool, transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(120)) }, label = "toolPanel") { t ->
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                when (t) {
                    EditorTool.Transform -> Spacer(Modifier.height(4.dp))
                    EditorTool.Filters -> FiltersPanel(source, live) { commit(live.copy(filter = it)) }
                    EditorTool.Tone -> TonePanel(live, onLive = { live = it }, onCommit = ::commit)
                    EditorTool.Decorations -> DecorationsPanel(live, onCommit = ::commit)
                    EditorTool.Tools -> ToolsPanel()
                }
            }
        }

        // Tool icons (icons only, 45dp cells, the chosen one yellow)
        Row(
            Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.navigationBars).padding(top = 8.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            EditorTool.entries.forEach { t ->
                Box(
                    Modifier.padding(horizontal = 1.dp).size(45.dp).clip(CircleShape).clickable { tool = t },
                    contentAlignment = Alignment.Center
                ) { Icon(t.icon, t.label, tint = if (t == tool) EditorYellow else Color.White, modifier = Modifier.size(26.dp)) }
            }
        }
    }

    if (leaving) LeaveDialog(
        onCancel = { leaving = false },
        onDiscard = { leaving = false; onClose() },
        onSave = { leaving = false; save() }
    )
}

@Composable
private fun BarIcon(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(Modifier.size(38.dp).clip(CircleShape).clickable(enabled = enabled, onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, label, tint = if (enabled) Color.White else Disabled, modifier = Modifier.size(24.dp))
    }
}

@Composable
private fun BarText(label: String, enabled: Boolean, onClick: () -> Unit) {
    Text(
        label, color = if (enabled) Color.White else Disabled, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable(enabled = enabled, onClick = onClick).padding(horizontal = 10.dp, vertical = 8.dp)
    )
}

/** The "Save or discard your changes?" card. */
@Composable
private fun LeaveDialog(onCancel: () -> Unit, onDiscard: () -> Unit, onSave: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        containerColor = Color(0xFF252527),
        shape = RoundedCornerShape(26.dp),
        text = { Text("Save or discard your changes?", color = Color.White, fontSize = 17.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
        confirmButton = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                TextButton(onClick = onCancel) { Text("Cancel", color = Color.White, fontSize = 15.sp) }
                TextButton(onClick = onDiscard) { Text("Discard", color = Color.White, fontSize = 15.sp) }
                TextButton(onClick = onSave) { Text("Save", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold) }
            }
        }
    )
}

// ── The picture: whole with the crop frame while transforming, otherwise the cropped result with its decorations ────

@Composable
private fun PictureArea(bmp: Bitmap, s: EditState, tool: EditorTool, boxW: Float, boxH: Float, onLive: (EditState) -> Unit, onCommit: (EditState) -> Unit) {
    val density = LocalDensity.current
    val turned = s.rotation % 180 != 0
    val imgW = if (turned) bmp.height.toFloat() else bmp.width.toFloat()
    val imgH = if (turned) bmp.width.toFloat() else bmp.height.toFloat()
    val image = remember(bmp) { bmp.asImageBitmap() }
    val colorFilter = ColorFilter.colorMatrix(androidx.compose.ui.graphics.ColorMatrix(s.matrix()))
    val transforming = tool == EditorTool.Transform
    // While transforming the whole picture shows; otherwise only the crop, enlarged to fill
    val region = if (transforming) Rect(0f, 0f, 1f, 1f) else s.crop
    val regionW = imgW * region.width
    val regionH = imgH * region.height
    val fit = minOf(boxW / regionW, boxH / regionH)
    val shownW = regionW * fit
    val shownH = regionH * fit
    Box(Modifier.size(shownW.dp, shownH.dp).clipToBounds()) {
        val fullW = imgW * fit
        val fullH = imgH * fit
        val straighten = s.straightenScale(imgW, imgH)
        Box(
            Modifier.size(fullW.dp, fullH.dp).offset((-region.left * fullW).dp, (-region.top * fullH).dp)
        ) {
            Image(
                image, null, contentScale = ContentScale.Fit, colorFilter = colorFilter,
                modifier = Modifier.size((if (turned) fullH else fullW).dp, (if (turned) fullW else fullH).dp).align(Alignment.Center)
                    .graphicsLayer {
                        rotationZ = s.rotation + s.straighten
                        val k = straighten
                        scaleX = (if (s.flip) -1f else 1f) * k
                        scaleY = k
                    }
            )
        }
        if (s.tone(Tone.Vignette) != 0f) VignetteOverlay(s.tone(Tone.Vignette))
        if (transforming) CropOverlay(s.crop, s.aspect?.let { it * imgH / imgW }, onChange = { onLive(s.copy(crop = it)) }, onEnd = { onCommit(s) })
        else DecorationLayer(s, editable = tool == EditorTool.Decorations, onLive = onLive, onCommit = onCommit)
    }
}

@Composable
private fun VignetteOverlay(amount: Float) {
    Canvas(Modifier.fillMaxSize()) {
        val dark = amount > 0
        drawRect(Brush.radialGradient(
            0.55f to Color.Transparent,
            1f to (if (dark) Color.Black else Color.White).copy(alpha = abs(amount) * 0.75f),
            center = center, radius = size.maxDimension * 0.75f
        ))
    }
}

/** The crop frame: corner and edge grips, thirds, the outside darkened. Corners resize, inside moves. */
@Composable
private fun CropOverlay(crop: Rect, fixed: Float?, onChange: (Rect) -> Unit, onEnd: () -> Unit) {
    var current by remember { mutableStateOf(crop) }
    current = crop
    Canvas(Modifier.fillMaxSize().systemGestureExclusion().pointerInput(fixed) {
        var corner = -1
        detectDragGestures(
            onDragStart = { p ->
                val r = current
                val corners = listOf(Offset(r.left, r.top), Offset(r.right, r.top), Offset(r.left, r.bottom), Offset(r.right, r.bottom))
                    .map { Offset(it.x * size.width, it.y * size.height) }
                corner = corners.indexOfFirst { (it - p).getDistance() < 56.dp.toPx() }
                if (corner < 0 && Rect(r.left * size.width, r.top * size.height, r.right * size.width, r.bottom * size.height).contains(p)) corner = 4
            },
            onDragEnd = onEnd,
            onDrag = { change, d ->
                change.consume()
                val dx = d.x / size.width
                val dy = d.y / size.height
                val r = current
                val min = 0.12f
                var next = when (corner) {
                    0 -> Rect((r.left + dx).coerceIn(0f, r.right - min), (r.top + dy).coerceIn(0f, r.bottom - min), r.right, r.bottom)
                    1 -> Rect(r.left, (r.top + dy).coerceIn(0f, r.bottom - min), (r.right + dx).coerceIn(r.left + min, 1f), r.bottom)
                    2 -> Rect((r.left + dx).coerceIn(0f, r.right - min), r.top, r.right, (r.bottom + dy).coerceIn(r.top + min, 1f))
                    3 -> Rect(r.left, r.top, (r.right + dx).coerceIn(r.left + min, 1f), (r.bottom + dy).coerceIn(r.top + min, 1f))
                    4 -> r.translate(dx.coerceIn(-r.left, 1f - r.right), dy.coerceIn(-r.top, 1f - r.bottom))
                    else -> r
                }
                if (fixed != null && corner in 0..3) {
                    val h = (next.width / fixed).coerceAtMost(1f)
                    next = if (corner < 2) Rect(next.left, (next.bottom - h).coerceAtLeast(0f), next.right, next.bottom)
                    else Rect(next.left, next.top, next.right, (next.top + h).coerceAtMost(1f))
                }
                current = next
                onChange(next)
            }
        )
    }) {
        val r = Rect(current.left * size.width, current.top * size.height, current.right * size.width, current.bottom * size.height)
        val shade = Color.Black.copy(alpha = 0.6f)
        drawRect(shade, Offset.Zero, Size(size.width, r.top))
        drawRect(shade, Offset(0f, r.bottom), Size(size.width, size.height - r.bottom))
        drawRect(shade, Offset(0f, r.top), Size(r.left, r.height))
        drawRect(shade, Offset(r.right, r.top), Size(size.width - r.right, r.height))
        drawRect(Color.White.copy(alpha = 0.9f), r.topLeft, r.size, style = Stroke(1.dp.toPx()))
        for (i in 1..2) {
            drawLine(Color.White.copy(alpha = 0.45f), Offset(r.left + r.width * i / 3, r.top), Offset(r.left + r.width * i / 3, r.bottom), 1.dp.toPx())
            drawLine(Color.White.copy(alpha = 0.45f), Offset(r.left, r.top + r.height * i / 3), Offset(r.right, r.top + r.height * i / 3), 1.dp.toPx())
        }
        // L-shaped corner grips and short edge grips
        val len = 22.dp.toPx(); val w = 3.dp.toPx()
        fun grip(x: Float, y: Float, sx: Float, sy: Float) {
            drawLine(Color.White, Offset(x, y), Offset(x + sx * len, y), w)
            drawLine(Color.White, Offset(x, y), Offset(x, y + sy * len), w)
        }
        grip(r.left, r.top, 1f, 1f); grip(r.right, r.top, -1f, 1f); grip(r.left, r.bottom, 1f, -1f); grip(r.right, r.bottom, -1f, -1f)
        val e = 14.dp.toPx()
        drawLine(Color.White, Offset(r.center.x - e, r.top), Offset(r.center.x + e, r.top), w)
        drawLine(Color.White, Offset(r.center.x - e, r.bottom), Offset(r.center.x + e, r.bottom), w)
        drawLine(Color.White, Offset(r.left, r.center.y - e), Offset(r.left, r.center.y + e), w)
        drawLine(Color.White, Offset(r.right, r.center.y - e), Offset(r.right, r.center.y + e), w)
    }
}

// ── Decorations: pen strokes and text/sticker items, in fractions of the cropped picture ─────────────────────────────

private enum class Pen(val label: String, val icon: ImageVector, val width: Float, val alpha: Float) {
    Pen("Pen", Sym.Rounded.Edit, 0.008f, 1f),
    Highlighter("Highlighter", Sym.Rounded.Ink_highlighter, 0.03f, 0.4f),
    Eraser("Eraser", Sym.Rounded.Ink_eraser, 0.03f, 1f)
}

private val penColors = listOf(Color.White, Color.Black, Color(0xFFFF453A), Color(0xFFFFD90D), Color(0xFF30D158), Color(0xFF0A84FF), Color(0xFFBF5AF2))

private object DecorationTool {
    var pen by mutableStateOf(Pen.Pen)
    var color by mutableStateOf(Color.White)
    var tab by mutableStateOf(0) // 0 draw, 1 stickers, 2 text
}

@Composable
private fun DecorationLayer(s: EditState, editable: Boolean, onLive: (EditState) -> Unit, onCommit: (EditState) -> Unit) {
    var drawing by remember { mutableStateOf<List<Offset>?>(null) }
    val drawMode = editable && DecorationTool.tab == 0
    Canvas(
        Modifier.fillMaxSize().then(if (!drawMode) Modifier else Modifier.pointerInput(DecorationTool.pen, DecorationTool.color, s) {
            detectDragGestures(
                onDragStart = { p -> drawing = listOf(Offset(p.x / size.width, p.y / size.height)) },
                onDragEnd = {
                    val pts = drawing.orEmpty()
                    drawing = null
                    if (DecorationTool.pen == Pen.Eraser) {
                        // The eraser removes whole strokes it passes over
                        val left = s.strokes.filterNot { st -> st.points.any { p -> pts.any { (it - p).getDistance() < 0.03f } } }
                        onCommit(s.copy(strokes = left))
                    } else if (pts.size > 1) onCommit(s.copy(strokes = s.strokes + DrawStroke(pts, DecorationTool.color.toArgb(), DecorationTool.pen.width, DecorationTool.pen.alpha)))
                },
                onDrag = { change, _ -> change.consume(); drawing = drawing.orEmpty() + Offset(change.position.x / size.width, change.position.y / size.height) }
            )
        })
    ) {
        fun stroke(points: List<Offset>, color: Color, width: Float, alpha: Float) {
            if (points.size < 2) return
            val path = Path().apply {
                moveTo(points[0].x * size.width, points[0].y * size.height)
                points.drop(1).forEach { lineTo(it.x * size.width, it.y * size.height) }
            }
            drawPath(path, color.copy(alpha = alpha), style = Stroke(width * size.minDimension, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        s.strokes.forEach { stroke(it.points, Color(it.color), it.width, it.alpha) }
        drawing?.takeIf { DecorationTool.pen != Pen.Eraser }?.let { stroke(it, DecorationTool.color, DecorationTool.pen.width, DecorationTool.pen.alpha) }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = constraints.maxWidth.toFloat(); val h = constraints.maxHeight.toFloat()
        val density = LocalDensity.current
        s.texts.forEachIndexed { i, item ->
            var drag by remember(item) { mutableStateOf(Offset.Zero) }
            Text(
                item.text, color = Color(item.color), fontSize = with(density) { (item.size * h).toSp() }, fontWeight = FontWeight.Bold,
                modifier = Modifier.offset { IntOffset((item.at.x * w + drag.x).roundToInt(), (item.at.y * h + drag.y).roundToInt()) }
                    .then(if (!editable) Modifier else Modifier.pointerInput(item) {
                        detectDragGestures(
                            onDragEnd = {
                                val moved = item.copy(at = Offset(item.at.x + drag.x / w, item.at.y + drag.y / h))
                                drag = Offset.Zero
                                onCommit(s.copy(texts = s.texts.toMutableList().also { it[i] = moved }))
                            },
                            onDrag = { change, d -> change.consume(); drag += d }
                        )
                    })
            )
        }
    }
}

@Composable
private fun DecorationsPanel(s: EditState, onCommit: (EditState) -> Unit) {
    var askText by remember { mutableStateOf(false) }
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        when (DecorationTool.tab) {
            0 -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // The pen pill: pens, then a divider, then undo/redo of strokes
                Row(Modifier.height(49.dp).clip(CircleShape).background(PanelGrey).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Pen.entries.forEach { p ->
                        Box(
                            Modifier.size(42.dp).clip(CircleShape).background(if (DecorationTool.pen == p) Color(0xFF3A3A3C) else Color.Transparent)
                                .clickable { DecorationTool.pen = p },
                            contentAlignment = Alignment.Center
                        ) { Icon(p.icon, p.label, tint = Color.White, modifier = Modifier.size(22.dp)) }
                    }
                    Box(Modifier.padding(horizontal = 8.dp).size(1.dp, 26.dp).background(Color(0xFF3A3A3C)))
                    Box(Modifier.size(42.dp).clip(CircleShape).clickable(enabled = s.strokes.isNotEmpty()) { onCommit(s.copy(strokes = s.strokes.dropLast(1))) }, contentAlignment = Alignment.Center) {
                        Icon(Sym.Rounded.Undo, "Undo stroke", tint = if (s.strokes.isNotEmpty()) Color.White else Disabled, modifier = Modifier.size(22.dp))
                    }
                }
                Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    penColors.forEach { c ->
                        Box(Modifier.size(24.dp).clip(CircleShape).background(c)
                            .border(if (DecorationTool.color == c) 2.dp else 1.dp, if (DecorationTool.color == c) EditorYellow else Color(0xFF3A3A3C), CircleShape)
                            .clickable { DecorationTool.color = c })
                    }
                }
            }
            1 -> LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(horizontal = 16.dp)) {
                itemsIndexed(listOf("😀", "😍", "😎", "🥳", "😂", "❤️", "🔥", "⭐", "👍", "🎉", "🌸", "🙏", "💯", "✨")) { _, e ->
                    Text(e, fontSize = 32.sp, modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable {
                        onCommit(s.copy(texts = s.texts + TextItem(e, Offset(0.42f, 0.42f), Color.White.toArgb(), 0.09f)))
                    }.padding(6.dp))
                }
            }
            else -> Text(
                "Add text", color = Color.Black, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clip(CircleShape).background(Color.White).clickable { askText = true }.padding(horizontal = 22.dp, vertical = 12.dp)
            )
        }
        Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(36.dp)) {
            listOf("Draw", "Stickers", "Text").forEachIndexed { i, label ->
                Text(label, color = if (DecorationTool.tab == i) EditorYellow else Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable { DecorationTool.tab = i })
            }
        }
    }
    if (askText) {
        var text by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { askText = false }, containerColor = Color(0xFF252527), shape = RoundedCornerShape(26.dp),
            text = { OutlinedTextField(text, { text = it }, singleLine = false, shape = RoundedCornerShape(16.dp)) },
            confirmButton = { TextButton(onClick = {
                askText = false
                if (text.isNotBlank()) onCommit(s.copy(texts = s.texts + TextItem(text, Offset(0.3f, 0.45f), DecorationTool.color.toArgb(), 0.06f)))
            }) { Text("Done", color = Color.White) } },
            dismissButton = { TextButton(onClick = { askText = false }) { Text("Cancel", color = Disabled) } }
        )
    }
}

// ── Transform, Filters, Tone, Tools panels ───────────────────────────────────────────────────────────────────────────

@Composable
private fun TransformPanel(s: EditState, source: Bitmap?, onLive: (EditState) -> Unit, onCommit: (EditState) -> Unit) {
    var sub by remember { mutableStateOf(0) } // 0 icons, 1 ratios, 2 straighten
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        when (sub) {
            1 -> LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(horizontal = 16.dp)) {
                itemsIndexed(listOf<Pair<String, Float?>>("Free" to null, "1:1" to 1f, "3:4" to 3f / 4f, "4:3" to 4f / 3f, "9:16" to 9f / 16f, "16:9" to 16f / 9f)) { _, (label, ratio) ->
                    Text(
                        label, fontSize = 13.sp, color = if (s.aspect == ratio) Color.Black else Color.White,
                        modifier = Modifier.clip(CircleShape).background(if (s.aspect == ratio) Color.White else PanelGrey).clickable {
                            onCommit(s.copy(aspect = ratio, crop = if (ratio == null) s.crop else fitAspect(ratio, source, s.rotation)))
                        }.padding(horizontal = 16.dp, vertical = 9.dp)
                    )
                }
            }
            2 -> {
                Text("${s.straighten.roundToInt()}°", color = Color.White, fontSize = 13.sp)
                TickRuler(value = s.straighten / 45f, onChange = { onLive(s.copy(straighten = it * 45f)) }, onEnd = { onCommit(s) })
            }
        }
        Spacer(Modifier.height(10.dp))
        // The transform capsule: flip, rotate, ratio, straighten
        Row(
            Modifier.clip(CircleShape).background(Color(0xFF0A0E12)).border(1.dp, Color(0xFF2C2F33), CircleShape).padding(horizontal = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            CapsuleIcon(Sym.Rounded.Flip, "Flip horizontally", false) { onCommit(s.copy(flip = !s.flip)) }
            CapsuleIcon(Sym.Rounded.Rotate_left, "Rotate", false) { onCommit(s.copy(rotation = (s.rotation + 270) % 360, crop = Rect(0f, 0f, 1f, 1f), aspect = null)) }
            CapsuleIcon(Sym.Rounded.Aspect_ratio, "Ratio", sub == 1) { sub = if (sub == 1) 0 else 1 }
            CapsuleIcon(Sym.Rounded.Straighten, "Straighten", sub == 2) { sub = if (sub == 2) 0 else 2 }
        }
    }
}

@Composable
private fun CapsuleIcon(icon: ImageVector, label: String, on: Boolean, onClick: () -> Unit) {
    Box(Modifier.size(34.dp).clip(CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, label, tint = if (on) EditorYellow else Color.White, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun FiltersPanel(source: Bitmap?, s: EditState, onPick: (Int) -> Unit) {
    val list = rememberLazyListState()
    val thumb = remember(source) { source?.let { Bitmap.createScaledBitmap(it, 180, (180f * it.height / it.width).toInt().coerceAtLeast(1), true).asImageBitmap() } }
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
        Text(photoFilters[s.filter].name, color = Color(0xFFE1E1E1), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(10.dp))
        LazyRow(state = list, horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            itemsIndexed(photoFilters) { i, f ->
                if (i == 1) Box(Modifier.padding(end = 8.dp).size(4.dp).clip(CircleShape).background(Color(0xFF8E8E93)))
                if (thumb != null) Image(
                    thumb, f.name, contentScale = ContentScale.Crop,
                    colorFilter = ColorFilter.colorMatrix(androidx.compose.ui.graphics.ColorMatrix(f.matrix)),
                    modifier = Modifier.size(64.dp).clip(RoundedCornerShape(16.dp))
                        .then(if (s.filter == i) Modifier.border(3.dp, Color.White, RoundedCornerShape(16.dp)) else Modifier)
                        .clickable { onPick(i) }
                )
            }
        }
    }
}

@Composable
private fun TonePanel(s: EditState, onLive: (EditState) -> Unit, onCommit: (EditState) -> Unit) {
    var active by remember { mutableStateOf(Tone.LightBalance) }
    val value = s.tones[active] ?: 0f
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
        Text(if (value != 0f) "${active.label}  ${value.roundToInt()}" else active.label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            // Auto: a balanced starting point
            Box(
                Modifier.padding(start = 20.dp).size(48.dp).clip(RoundedCornerShape(12.dp)).background(PanelGrey).clickable {
                    onCommit(s.copy(tones = s.tones + mapOf(Tone.LightBalance to 25f, Tone.Contrast to 12f, Tone.Saturation to 10f)))
                },
                contentAlignment = Alignment.Center
            ) { Text("Auto", color = Color.White, fontSize = 13.sp) }
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(horizontal = 14.dp)) {
                itemsIndexed(Tone.entries) { _, t ->
                    val on = t == active
                    Box(
                        Modifier.size(60.dp).clip(CircleShape).background(if (on) Color(0xFFFAFAFA) else RoundGrey).clickable { active = t },
                        contentAlignment = Alignment.Center
                    ) { Icon(t.icon, t.label, tint = if (on) Color.Black else Color.White, modifier = Modifier.size(24.dp)) }
                }
            }
        }
        TickRuler(value / 100f, onChange = { onLive(s.copy(tones = s.tones + (active to it * 100f))) }, onEnd = { onCommit(s) })
    }
}

/** The ruler slider: grey ticks, a yellow mark in the middle; drag sideways to move the value (-1..1). */
@Composable
private fun TickRuler(value: Float, onChange: (Float) -> Unit, onEnd: () -> Unit) {
    var v by remember { mutableStateOf(value) }
    v = value
    Canvas(Modifier.fillMaxWidth().height(34.dp).systemGestureExclusion().padding(horizontal = 28.dp).pointerInput(Unit) {
        detectHorizontalDragGestures(onDragEnd = onEnd) { change, dx ->
            change.consume()
            v = (v + dx / (size.width / 2f)).coerceIn(-1f, 1f)
            onChange(if (abs(v) < 0.02f) 0f else v)
        }
    }.pointerInput(Unit) { detectTapGestures(onDoubleTap = { v = 0f; onChange(0f); onEnd() }) }) {
        val step = 8.dp.toPx()
        val mid = size.width / 2
        val shift = -v * size.width / 2
        var x = mid + shift % step - step * 60
        while (x < size.width + step) {
            if (x >= 0) {
                val major = abs(((x - mid - shift) / step).roundToInt()) % 5 == 0
                drawLine(Color(0xFF6C6C6D), Offset(x, size.height * (if (major) 0.25f else 0.38f)), Offset(x, size.height * (if (major) 0.75f else 0.62f)), 1.5.dp.toPx())
            }
            x += step
        }
        // Where zero is, then the value mark
        drawLine(Color.White.copy(alpha = 0.7f), Offset(mid + shift, size.height * 0.2f), Offset(mid + shift, size.height * 0.8f), 1.5.dp.toPx())
        drawLine(EditorYellow, Offset(mid, 0f), Offset(mid, size.height), 2.5.dp.toPx())
    }
}

@Composable
private fun ToolsPanel() {
    val context = LocalContext.current
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf("Object eraser" to Sym.Rounded.Ink_eraser, "Lasso" to Sym.Rounded.Gesture, "Spot colour" to Sym.Rounded.Colorize, "Colour mix" to Sym.Rounded.Palette).forEach { (label, icon) ->
            Column(
                Modifier.weight(1f).height(96.dp).clip(RoundedCornerShape(18.dp)).background(PanelGrey).clickable {
                    Toast.makeText(context, "$label is coming in a later version", Toast.LENGTH_SHORT).show()
                }.padding(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
            ) {
                Icon(icon, label, tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(24.dp))
                Text(label, color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
}

// ── Loading and saving ───────────────────────────────────────────────────────────────────────────────────────────────

/** The biggest crop of [ratio] (width / height) centred on the picture, as fractions. */
private fun fitAspect(ratio: Float, bmp: Bitmap?, rotation: Int): Rect {
    bmp ?: return Rect(0f, 0f, 1f, 1f)
    val w = if (rotation % 180 != 0) bmp.height.toFloat() else bmp.width.toFloat()
    val h = if (rotation % 180 != 0) bmp.width.toFloat() else bmp.height.toFloat()
    return if (w / h > ratio) { val f = ratio * h / w; Rect((1 - f) / 2, 0f, (1 + f) / 2, 1f) }
    else { val f = w / ratio / h; Rect(0f, (1 - f) / 2, 1f, (1 + f) / 2) }
}

/** The picture at most 2560px on its long side, upright (EXIF rotation applied). */
private fun loadForEditing(context: Context, uri: Uri): Bitmap? = try {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 2560) sample *= 2
    val bmp = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
    val degrees = context.contentResolver.openInputStream(uri)?.use {
        when (android.media.ExifInterface(it).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, 1)) {
            android.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90
            android.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180
            android.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }
    } ?: 0
    if (bmp != null && degrees != 0) Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(degrees.toFloat()) }, true) else bmp
} catch (e: Exception) { null }

/** The finished picture: rotate, flip, straighten, crop, colour, vignette, pen strokes and text. */
fun render(source: Bitmap, s: EditState): Bitmap {
    val turned = Bitmap.createBitmap(source, 0, 0, source.width, source.height, Matrix().apply {
        postRotate(s.rotation.toFloat())
        if (s.flip) postScale(-1f, 1f)
    }, true)
    // Straighten: turn about the centre and enlarge so no corner is empty
    val straight = if (s.straighten == 0f) turned else {
        val k = s.straightenScale(turned.width.toFloat(), turned.height.toFloat())
        Bitmap.createBitmap(turned.width, turned.height, Bitmap.Config.ARGB_8888).also { out ->
            android.graphics.Canvas(out).apply {
                translate(out.width / 2f, out.height / 2f); rotate(s.straighten); scale(k, k); translate(-out.width / 2f, -out.height / 2f)
                drawBitmap(turned, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
            }
        }
    }
    val x = (s.crop.left * straight.width).toInt().coerceIn(0, straight.width - 1)
    val y = (s.crop.top * straight.height).toInt().coerceIn(0, straight.height - 1)
    val w = (s.crop.width * straight.width).toInt().coerceIn(1, straight.width - x)
    val h = (s.crop.height * straight.height).toInt().coerceIn(1, straight.height - y)
    val cropped = Bitmap.createBitmap(straight, x, y, w, h)
    val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(out)
    canvas.drawBitmap(cropped, 0f, 0f, Paint().apply { colorFilter = ColorMatrixColorFilter(s.matrix()) })
    val vig = s.tone(Tone.Vignette)
    if (vig != 0f) canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), Paint().apply {
        val edge = (if (vig > 0) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
        val a = (abs(vig) * 0.75f * 255).toInt()
        shader = RadialGradient(w / 2f, h / 2f, maxOf(w, h) * 0.75f, intArrayOf(0, 0, (a shl 24) or (edge and 0xFFFFFF)), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
    })
    val minDim = minOf(w, h).toFloat()
    s.strokes.forEach { st ->
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = st.color; alpha = (st.alpha * 255).toInt(); style = Paint.Style.STROKE
            strokeWidth = st.width * minDim; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
        }
        val path = android.graphics.Path().apply {
            moveTo(st.points[0].x * w, st.points[0].y * h)
            st.points.drop(1).forEach { lineTo(it.x * w, it.y * h) }
        }
        canvas.drawPath(path, paint)
    }
    s.texts.forEach { t ->
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = t.color; textSize = t.size * h; isFakeBoldText = true }
        t.text.lines().forEachIndexed { i, line -> canvas.drawText(line, t.at.x * w, t.at.y * h + paint.textSize * (i + 1), paint) }
    }
    return out
}

/** Writes a JPEG to Pictures/Edited through MediaStore. */
fun saveEdited(context: Context, bitmap: Bitmap, folder: String = "Edited", prefix: String = "Edited"): Boolean = try {
    val values = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, "${prefix}_${System.currentTimeMillis()}.jpg")
        put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
        if (Build.VERSION.SDK_INT >= 29) {
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/" + folder)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
    }
    val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)!!
    context.contentResolver.openOutputStream(uri)!!.use { bitmap.compress(Bitmap.CompressFormat.JPEG, 95, it) }
    if (Build.VERSION.SDK_INT >= 29) context.contentResolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
    true
} catch (e: Exception) { e.printStackTrace(); false }
