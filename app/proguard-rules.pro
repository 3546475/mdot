# —— kotlinx.serialization：备份 DTO 字段名参与 JSON，保留生成的序列化器 ——
-keepattributes *Annotation*, InnerClasses, Signature
-dontnote kotlinx.serialization.**
-keep,includedescriptorclasses class com.mdot.app.**$$serializer { *; }
-keepclassmembers class com.mdot.app.** {
    *** Companion;
}
-keepclasseswithmembers class com.mdot.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# —— SQLCipher：JNI 绑定类不混淆 ——
-keep class net.zetetic.** { *; }

# —— OkHttp / Conscrypt 信任缓存提示降噪 ——
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn okhttp3.internal.platform.**
-dontwarn org.codehaus.mojo.animal_sniffer.*

# —— Coroutines 调试信息降噪 ——
-dontwarn kotlinx.coroutines.debug.**
