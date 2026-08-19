-keep class com.marbleng.app.nativebridge.HevTunnel { *; }
-keepclasseswithmembers class * { native <methods>; }
-dontwarn org.jetbrains.annotations.**

# MARBLE_SSH_R8_V25
-keep class com.jcraft.jsch.** { *; }
-dontwarn com.jcraft.jsch.**
-dontwarn org.bouncycastle.**
-dontwarn com.sun.jna.**
-dontwarn org.newsclub.net.unix.**
-dontwarn org.apache.logging.log4j.**
-dontwarn org.slf4j.**
