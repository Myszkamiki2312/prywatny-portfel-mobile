# R8 keep rules for the offline WebView + embedded Ktor backend.

# CRITICAL: the JS bridge relies on reflection. R8 must not rename or strip any
# @JavascriptInterface method, otherwise window.AndroidOffline.getOfflineToken() breaks
# and every /api/* call fails the token check.
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Ktor (CIO server engine) + its transitive stack use reflection / service loading.
# Keep broadly — runtime breakage here is silent and hard to diagnose.
-keep class io.ktor.** { *; }
-keepclassmembers class io.ktor.** { *; }
-dontwarn io.ktor.**

# Coroutines (Ktor + repository run on them).
-keep class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**

# kotlinx.serialization (ktor-serialization-kotlinx-json).
-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod
-dontnote kotlinx.serialization.**
-dontwarn kotlinx.serialization.**
-keepclassmembers class **$$serializer { *; }
-keep,includedescriptorclasses class * { @kotlinx.serialization.Serializable <fields>; }
-keepclasseswithmembers class * { @kotlinx.serialization.Serializable *; }

# SLF4J / logging pulled in by Ktor — only used optionally.
-dontwarn org.slf4j.**

# Our own offline package: Room entities/DAO are referenced reflectively by the generated
# implementation; keep to avoid any field/name mismatch after shrinking.
-keep class pl.prywatnyportfel.mobile.offline.** { *; }

# Room runtime.
-keep class androidx.room.** { *; }
-dontwarn androidx.room.paging.**
