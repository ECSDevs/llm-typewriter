import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_11) }
}

// 签名密钥配置：CI 环境变量优先，本地 local.properties 兜底
val localProps = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use(::load)
}
fun signProp(name: String): String? =
    System.getenv(name) ?: localProps.getProperty(name)

android {
    namespace = "cc.ptoe.llmtypewriter.sample.android"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "cc.ptoe.llmtypewriter.sample"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 1
        versionName = "0.1.0"
    }

    signingConfigs {
        create("release") {
            storeFile = signProp("SAMPLE_STORE_FILE")?.let { rootProject.file(it) }
            storePassword = signProp("SAMPLE_STORE_PASSWORD")
            keyAlias = signProp("SAMPLE_KEY_ALIAS")
            keyPassword = signProp("SAMPLE_KEY_PASSWORD")
        }
    }

    buildFeatures { compose = true }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    // AndroidMath (transitive via :llm-typewriter) pulls in guava-18.0 which already contains
    // ListenableFuture, and the standalone `com.google.guava:listenablefuture:1.0` duplicates it.
    configurations.all {
        exclude(group = "com.google.guava", module = "listenablefuture")
    }
}

dependencies {
    implementation(project(":sample:composeApp"))
    implementation(libs.androidx.activity.compose)
    implementation(compose.material3)
}
