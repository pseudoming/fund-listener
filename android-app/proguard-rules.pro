# ── Ktor ──────────────────────────────────────────
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**
-keep class io.netty.** { *; }
-dontwarn io.netty.**

# ── Kotlin serialization ──────────────────────────
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.fundlistener.**$$serializer { *; }
-keepclassmembers class com.fundlistener.** {
    *** Companion;
}
-keepclasseswithmembers class com.fundlistener.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ── Koin ──────────────────────────────────────────
-keep class org.koin.** { *; }
-dontwarn org.koin.**

# ── Room ──────────────────────────────────────────
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# ── ML Kit ────────────────────────────────────────
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# ── OkHttp / Jsoup ────────────────────────────────
-keep class okhttp3.** { *; }
-dontwarn okhttp3.**
-keep class org.jsoup.** { *; }
-dontwarn org.jsoup.**
