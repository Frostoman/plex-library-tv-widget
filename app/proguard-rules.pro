# Minification is disabled by default, but keep the HTTP libs safe if enabled.
-keep class okhttp3.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**
