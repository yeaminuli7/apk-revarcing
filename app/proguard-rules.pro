# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# ML Kit on-device translation. Needed only if minify is turned on later.
-keep class com.google.mlkit.nl.translate.** { *; }

# ---------------------------------------------------------------------------
# R8 (minify) rules — enabled in release builds from BalaSpeak 2.0
# ---------------------------------------------------------------------------

# Keep line numbers so crash traces stay readable
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Room: entities are reflectively mapped; keep their columns intact
-keep @androidx.room.Entity class * { *; }
-keep class * extends androidx.room.RoomDatabase { *; }

# Moshi + Retrofit (converter-moshi resolves adapters reflectively)
-keepnames @com.squareup.moshi.JsonClass class *
-keepclassmembers class * { @com.squareup.moshi.Json <fields>; }
-keep class * extends com.squareup.moshi.JsonAdapter { <init>(...); }
-keepattributes Signature, InnerClasses, EnclosingMethod, *Annotation*
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Coroutines debug metadata is safe to drop
-dontwarn kotlinx.coroutines.debug.**

# sherpa-onnx: JNI binds these by name — never shrink/rename
-keep class com.k2fsa.sherpa.onnx.** { *; }
-dontwarn com.k2fsa.sherpa.onnx.**

# ---------------------------------------------------------------------------
# ONNX Runtime Java API (com.microsoft.onnxruntime): JNI resolves Java classes
# by name, so renaming them breaks inference (Bangla MMS-TTS pack). Keep intact.
# ---------------------------------------------------------------------------
-keep class ai.onnxruntime.** { *; }
-dontwarn ai.onnxruntime.**

# Apache commons-compress references optional codecs (xz/zstd/brotli) that are
# intentionally absent; silence link warnings for those.
-dontwarn org.apache.commons.compress.**
-dontwarn org.tukaani.xz.**
-dontwarn com.github.luben.zstd.**
-dontwarn org.brotli.**
