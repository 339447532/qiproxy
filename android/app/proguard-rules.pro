# ProGuard rules for qiproxy client
# Keep protocol classes (may be accessed via reflection)
-keep class com.qiproxy.client.protocol.** { *; }
-keep class com.qiproxy.client.config.** { *; }
-keep class com.qiproxy.client.core.** { *; }

# BouncyCastle
-dontwarn org.bouncycastle.**
-keep class org.bouncycastle.** { *; }
