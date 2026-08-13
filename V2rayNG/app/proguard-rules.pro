# MobileTina release hardening.
#
# R8 may optimize, shrink and rename implementation details. Runtime contracts used by
# Gson/MMKV, WorkManager, Android Parcelable and JNI must remain stable across updates.

# Gson restores DTO fields reflectively from V2Ray configs, subscription records and
# MMKV. DTO class names are not part of the persisted format, so classes may still be
# renamed and optimized. Persisted field members themselves must NOT be optimized away
# or renamed, even when compiled Kotlin code does not directly read every field.
-keep,allowoptimization,allowobfuscation class com.v2ray.ang.dto.**
-keepclassmembers class com.v2ray.ang.dto.** {
    <fields>;
}

# Gson serializes enums by constant name. Keep enum constants stable so values already
# stored in MMKV (VMESS, VLESS, TROJAN, etc.) remain readable after an app update.
# The enum class name may still be obfuscated because it is not persisted.
-keep,allowobfuscation enum com.v2ray.ang.enums.**
-keepclassmembers enum com.v2ray.ang.enums.** {
    <fields>;
}

# Generic signatures and Gson annotations describe runtime JSON types.
-keepattributes Signature,*Annotation*
-keep class * extends com.google.gson.reflect.TypeToken

# WorkManager persists worker class names in its database.
-keepnames class * extends androidx.work.ListenableWorker
-keepclassmembers class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# JNI entry points must retain names expected by bundled native libraries.
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}

# Parcelable creators are looked up by Android framework code.
-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator CREATOR;
}

# Allow R8 to inline/merge implementation details not protected by the runtime contracts
# above. This preserves aggressive obfuscation without sacrificing persisted config data.
-allowaccessmodification
