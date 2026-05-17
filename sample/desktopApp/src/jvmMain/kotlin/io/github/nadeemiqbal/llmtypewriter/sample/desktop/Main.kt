package io.github.nadeemiqbal.llmtypewriter.sample.desktop

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import io.github.nadeemiqbal.llmtypewriter.sample.SampleApp

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "LlmTypewriter Sample",
        state = rememberWindowState(width = 600.dp, height = 800.dp),
    ) {
        SampleApp()
    }
}
