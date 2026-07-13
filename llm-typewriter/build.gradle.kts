import org.jetbrains.compose.ExperimentalComposeLibrary
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.vanniktech.maven.publish)
}

// Release version is driven by the git tag on CI: tag `v0.2.0` publishes `0.2.0`.
// Override locally with `-Pversion=...`; otherwise builds use the literal below.
val libVersion: String =
    (System.getenv("RELEASE_VERSION") ?: findProperty("version") as String?)
        ?.removePrefix("v")
        ?.takeUnless { it.isBlank() || it == "unspecified" }
        ?: "0.1.0"

group = "cc.ptoe"
version = libVersion

kotlin {
    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    applyDefaultHierarchyTemplate()

    androidTarget {
        publishLibraryVariants("release")
        compilations.all {
            compileTaskProvider.configure {
                compilerOptions { jvmTarget.set(JvmTarget.JVM_11) }
            }
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.animation)
            implementation(compose.ui)
            implementation(compose.material3)
            implementation(libs.coil.compose)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.highlights)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }

        // Android-only target — AndroidMath's MTMathView (native Freetype rendering) lives here.
        // Resolved from JitPack (see settings.gradle.kts).
        val androidMain by getting {
            dependencies {
                // Exclude declared on the dependency itself (not via `configurations.all`) so
                // Gradle emits `<exclusions>` in the published POM — downstream consumers won't
                // pull in `com.google.guava:listenablefuture:1.0`, whose classes duplicate the
                // `ListenableFuture` already shipped inside AndroidMath's transitive guava-18.0.
                implementation("com.github.gregcockroft:AndroidMath:v1.1.0") {
                    exclude(group = "com.google.guava", module = "listenablefuture")
                }
                implementation(libs.coil.network.okhttp)
            }
        }

        // Compose UI tests — Android instrumented tests backed by `compose.uiTest`.
        // Pure-logic tests live in `commonTest` (run as `androidUnitTest`).
        val androidInstrumentedTest by getting {
            dependencies {
                @OptIn(ExperimentalComposeLibrary::class)
                implementation(compose.uiTest)
            }
        }
    }
}

android {
    namespace = "cc.ptoe.llmtypewriter"
    compileSdk = libs.versions.compileSdk.get().toInt()
    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

mavenPublishing {
    publishToMavenCentral()

    if (
        project.hasProperty("signingInMemoryKey") ||
        project.hasProperty("signing.keyId")
    ) {
        signAllPublications()
    }

    coordinates("cc.ptoe", "llm-typewriter", libVersion)

    pom {
        name.set("LlmTypewriter")
        description.set(
            "Streaming-text typewriter composable for LLM apps on Compose — " +
                "renders a Flow<String> token stream with live progressive Markdown, " +
                "syntax-highlighted code blocks that build up as tokens arrive, a configurable " +
                "three speed curves (linear/easeOut/natural), tap-to-skip, " +
                "graceful stop-mid-stream, selectable text, and screen-reader-friendly a11y.",
        )
        inceptionYear.set("2026")
        url.set("https://github.com/ECSDevs/llm-typewriter")
        licenses {
            license {
                name.set("The Apache License, Version 2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                distribution.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
            }
        }
        developers {
            // Upstream author of llm-typewriter (this project is a fork — see
            // README "Credits" and LICENSE for the full attribution notice).
            developer {
                id.set("NadeemIqbal")
                name.set("NadeemIqbal")
                url.set("https://github.com/NadeemIqbal")
            }
            developer {
                id.set("originalFactor")
                name.set("originalFactor")
                email.set("2438926613@qq.com")
                url.set("https://github.com/originalFactor")
            }
        }
        scm {
            url.set("https://github.com/ECSDevs/llm-typewriter")
            connection.set("scm:git:git://github.com/ECSDevs/llm-typewriter.git")
            developerConnection.set("scm:git:ssh://git@github.com/ECSDevs/llm-typewriter.git")
        }
    }
}
