package cc.ptoe.llmtypewriter

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import cc.ptoe.llmtypewriter.R

internal actual val PlatformCodeBlockFontFamily: FontFamily = FontFamily(
    Font(resId = R.font.cascadia_code_regular, weight = FontWeight.Normal),
    Font(resId = R.font.cascadia_code_italic, weight = FontWeight.Normal, style = FontStyle.Italic),
    Font(resId = R.font.cascadia_code_semibold, weight = FontWeight.SemiBold),
    Font(
        resId = R.font.cascadia_code_semibold_italic,
        weight = FontWeight.SemiBold,
        style = FontStyle.Italic,
    ),
)
