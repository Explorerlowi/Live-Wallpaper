# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# 保留行号信息，便于崩溃时定位问题
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Kotlin 相关
-keep class kotlin.** { *; }
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**
-keepclassmembers class **$WhenMappings {
    <fields>;
}
-keepclassmembers class kotlin.Metadata {
    public <methods>;
}

# Kotlinx Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.example.livewallpaper.**$$serializer { *; }
-keepclassmembers class com.example.livewallpaper.** {
    *** Companion;
}
-keepclasseswithmembers class com.example.livewallpaper.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Koin 依赖注入
-keep class org.koin.** { *; }
-dontwarn org.koin.**

# Coil 图片加载库
-keep class coil.** { *; }
-dontwarn coil.**

# Compose 相关
-keep class androidx.compose.** { *; }
-keep class androidx.compose.runtime.** { *; }

# 保留数据类（如果使用反射）
-keep class com.example.livewallpaper.** { *; }
-keepclassmembers class com.example.livewallpaper.** {
    <fields>;
}

# 保留 Parcelable 实现
-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

# 保留 Service（动态壁纸服务）
-keep class com.example.livewallpaper.LiveWallpaperService { *; }
-keep class com.example.livewallpaper.LiveWallpaperEngine { *; }

# SLF4J 日志库
-dontwarn org.slf4j.**
-keep class org.slf4j.** { *; }
-dontwarn org.slf4j.impl.StaticLoggerBinder
