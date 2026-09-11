# MLKit
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.** { *; }

# Moshi
-keepclassmembers class * {
    @com.squareup.moshi.JsonClass *;
    <fields>;
}
-keep @com.squareup.moshi.JsonClass class * { *; }

# Kotlin Metadata
-keep class kotlin.Metadata { *; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# DataStore
-keep class androidx.datastore.** { *; }

# Application & 保留入口
-keep class com.ltt.gkd.App { *; }
-keep class com.ltt.gkd.service.SkipAccessibilityService { *; }

# Moshi 反射回退（KSP 生成的 adapter 已覆盖，此为兜底）
-keep class com.squareup.moshi.adapters.** { *; }
