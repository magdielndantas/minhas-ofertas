# Keep TDLib JNI bindings and native methods
-keep class org.drinkless.tdlib.** { *; }
-dontwarn org.drinkless.tdlib.**

# Keep Compose & Kotlin reflection
-keepattributes *Annotation*
-dontwarn sun.misc.Unsafe
