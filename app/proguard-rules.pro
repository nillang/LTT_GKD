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
