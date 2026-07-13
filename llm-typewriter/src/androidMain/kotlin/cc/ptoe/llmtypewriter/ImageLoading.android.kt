package cc.ptoe.llmtypewriter

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import coil3.ImageLoader
import coil3.compose.LocalPlatformContext
import coil3.network.okhttp.OkHttpNetworkFetcherFactory

@Composable
internal actual fun rememberPlatformImageLoader(): ImageLoader {
    val context = LocalPlatformContext.current
    return remember(context) {
        ImageLoader.Builder(context)
            .components {
                add(OkHttpNetworkFetcherFactory())
            }
            .build()
    }
}

internal actual fun logPlatformImageLoadError(url: String, throwable: Throwable?) {
    Log.e("LlmTypewriter", "Markdown image load failed for $url", throwable)
}
