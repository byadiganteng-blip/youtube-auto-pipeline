# ═══════════════════════════════════════════════════════════
# ANTI-DECOMPILE / OBFUSCATION
# ═══════════════════════════════════════════════════════════

-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

-repackageclasses ''
-allowaccessmodification
-overloadaggressively
-useuniqueclassmembernames

-adaptclassstrings
-adaptresourcefilecontents **.properties,META-INF/MANIFEST.MF
-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod

# Remove logging
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}
-assumenosideeffects class com.universal.videoeditor.LogTracker {
    public static *** d(...);
    public static *** i(...);
}

# Keep Activity/Service (dipanggil system)
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Application
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider
-keep class com.universal.videoeditor.MainActivity { *; }
-keep class com.universal.videoeditor.YadApp { *; }
-keep class com.universal.videoeditor.FloatingProgressService { *; }
-keep class com.universal.videoeditor.ResultsActivity { *; }
-keep class com.universal.videoeditor.InstructionsActivity { *; }
-keep class com.universal.videoeditor.CreditActivity { *; }

# JSON
-keep class org.json.** { *; }
-keepclassmembers class * {
    @org.json.JSONObject <methods>;
}

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }

# Kotlin
-dontwarn kotlin.**
-keep class kotlin.Metadata { *; }

# Start.io SDK — WAJIB (reflection)
-keep class com.ironsource.** { *; }
-keep interface com.ironsource.** { *; }
-dontwarn com.ironsource.**

# AndroidX Security
-keep class androidx.security.crypto.** { *; }

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# BuildConfig
-keep class com.universal.videoeditor.BuildConfig { *; }

# Obfuscate utility classes (lebih agresif)
-keep,allowobfuscation class com.universal.videoeditor.ProcessRunner { *; }
-keep,allowobfuscation class com.universal.videoeditor.HistoryManager { *; }
-keep,allowobfuscation class com.universal.videoeditor.NotifFetcher { *; }
-keep,allowobfuscation class com.universal.videoeditor.UpdateChecker { *; }
-keep,allowobfuscation class com.universal.videoeditor.SecureConfig { *; }
-keep,allowobfuscation class com.universal.videoeditor.StartIoAds { *; }
