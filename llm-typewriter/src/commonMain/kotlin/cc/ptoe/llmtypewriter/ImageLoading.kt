package cc.ptoe.llmtypewriter

import androidx.compose.runtime.Composable
import coil3.ImageLoader

@Composable
internal expect fun rememberPlatformImageLoader(): ImageLoader

internal expect fun logPlatformImageLoadError(url: String, throwable: Throwable?)
