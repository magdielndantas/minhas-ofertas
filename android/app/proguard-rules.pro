# Keep TDLib JNI bindings and native methods
-keep class org.drinkless.tdlib.** { *; }
-dontwarn org.drinkless.tdlib.**

# Google Crypto / Tink (Security Crypto)
-dontwarn com.google.errorprone.annotations.**
-dontwarn com.google.crypto.tink.**

# Room Database
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# Keep Compose & Kotlin reflection
-keepattributes *Annotation*
-dontwarn sun.misc.Unsafe
