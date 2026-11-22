package com.elvan.gallery.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.res.ResourcesCompat

/** Short sayings from public-domain writers and proverbs. */
private val sayings = listOf(
    "Well done is better than well said." to "Benjamin Franklin",
    "The journey of a thousand miles begins with a single step." to "Lao Tzu",
    "Knowing yourself is the beginning of all wisdom." to "Aristotle",
    "Nothing is permanent except change." to "Heraclitus",
    "Dwell on the beauty of life." to "Marcus Aurelius",
    "Learning never exhausts the mind." to "Leonardo da Vinci",
    "Simplicity is the ultimate sophistication." to "Leonardo da Vinci",
    "What we think, we become." to "Buddha",
    "Fall seven times, stand up eight." to "Japanese proverb",
    "Little by little, one travels far." to "J. R. R. Tolkien",
    "Light tomorrow with today." to "Elizabeth Barrett Browning",
    "Act as if what you do makes a difference. It does." to "William James",
    "It always seems impossible until it's done." to "Proverb",
    "The best way out is always through." to "Robert Frost",
    "Be yourself; everyone else is already taken." to "Oscar Wilde",
    "Patience is bitter, but its fruit is sweet." to "Jean-Jacques Rousseau"
)

/** Draws a random saying on a card of [background] colour, in Elvan Sans, light or dark text to suit it. */
fun quoteCard(context: Context, background: Int): Bitmap {
    val (text, author) = sayings.random()
    val w = 1080; val h = 1350
    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawColor(background)
    val luminance = (0.299 * Color.red(background) + 0.587 * Color.green(background) + 0.114 * Color.blue(background)) / 255
    val ink = if (luminance > 0.6) Color.BLACK else Color.WHITE
    val bold = ResourcesCompat.getFont(context, com.elvan.navil.R.font.elvan_sans_bold) ?: Typeface.DEFAULT_BOLD
    val regular = ResourcesCompat.getFont(context, com.elvan.navil.R.font.elvan_sans_regular) ?: Typeface.DEFAULT
    val body = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = ink; textSize = 72f; typeface = bold }
    val layout = StaticLayout.Builder.obtain("“$text”", 0, text.length + 2, body, (w * 0.8).toInt())
        .setAlignment(Layout.Alignment.ALIGN_CENTER).setLineSpacing(0f, 1.15f).build()
    val by = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = ink; alpha = 190; textSize = 40f; typeface = regular; textAlign = Paint.Align.CENTER }
    val top = (h - layout.height - 120) / 2f
    canvas.save(); canvas.translate(w * 0.1f, top); layout.draw(canvas); canvas.restore()
    canvas.drawText("— $author", w / 2f, top + layout.height + 100, by)
    return bitmap
}
