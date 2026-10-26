-keep class com.drdev.faphousepro.security.NativeSecurityBridge { *; }
-keepclasseswithmembernames class * { native <methods>; }

-keep class com.drdev.faphousepro.FapHouseProApp { *; }
-keep class com.drdev.faphousepro.** extends android.app.Application { *; }
-keep class com.drdev.faphousepro.ui.** { *; }

-keepattributes *Annotation*, InnerClasses
-keep,includedescriptorclasses class com.drdev.faphousepro.**$$serializer { *; }
-keepclassmembers class com.drdev.faphousepro.** { *** Companion; }
-keepclasseswithmembers class com.drdev.faphousepro.** { kotlinx.serialization.KSerializer serializer(...); }
-keep @kotlinx.serialization.Serializable class com.drdev.faphousepro.** { *; }

-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

-dontwarn androidx.media3.**
-keep class androidx.media3.** { *; }
-keep class * extends androidx.media3.exoplayer.source.MediaSource$Factory { *; }

-keep class androidx.webkit.** { *; }
-keep @androidx.webkit.JavascriptInterface class * { *; }

-keepclassmembernames class kotlinx.coroutines.internal.MainDispatcherFactory { *; }
-keepclassmembernames class kotlinx.coroutines.CoroutineExceptionHandler { *; }
-keepclassmembernames class kotlinx.coroutines.android.AndroidDispatcherFactory { *; }
-dontwarn kotlinx.coroutines.**

-keep class androidx.datastore.** { *; }
-keep class androidx.security.crypto.** { *; }
-keep class androidx.work.** { *; }
-dontwarn androidx.work.**

-keep class com.drdev.faphousepro.ui.main.PlayerBridge { *; }

-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
}

-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
-dontwarn javax.annotation.**
-dontwarn kotlin.**