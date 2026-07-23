/*
 * Copyright 2026 ECSDevs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cc.ptoe.llmtypewriter

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.TextUnit

/**
 * Desktop JVM actual: AndroidMath (native Freetype) is Android-only, so math
 * fragments are rendered as their LaTeX source in a monospace style. Measuring
 * uses the same string so inline placeholders stay consistent with rendering.
 */
@Composable
internal actual fun RenderPlatformMath(
    latex: String,
    displayMode: Boolean,
    textColor: Color,
    fontSize: TextUnit,
    modifier: Modifier,
) {
    Text(
        text = if (displayMode) "$$ $latex $$" else latex,
        color = textColor,
        style = TextStyle(fontSize = fontSize, fontFamily = FontFamily.Monospace),
        modifier = modifier,
    )
}

@Composable
internal actual fun measurePlatformMath(
    latex: String,
    displayMode: Boolean,
    fontSize: TextUnit,
): IntSize {
    if (latex.isEmpty()) return IntSize.Zero
    val textMeasurer = rememberTextMeasurer()
    return textMeasurer.measure(
        text = AnnotatedString(if (displayMode) "$$ $latex $$" else latex),
        style = TextStyle(fontSize = fontSize, fontFamily = FontFamily.Monospace),
    ).size
}
