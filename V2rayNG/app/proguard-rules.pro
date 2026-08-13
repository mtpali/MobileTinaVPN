# MobileTina release hardening.
#
# R8 may optimize, shrink and rename implementation details. Only runtime contracts
# consumed by Gson/MMKV, WorkManager, Android Parcelable and JNI stay stable.

# Gson restores DTO fields reflectively from V2Ray configs, subscription records and
# MMKV. Keep DTO classes live but allow class-name obfuscation; keep only their fields
# stable so methods/constructors and implementation details remain shrinkable.
-keep,allowoptimization,allowobfuscation class com.v2ray.ang.dto.**
-keepclassmembers,allowoptimization class com.v2ray.ang.dto.** {
    <fields>;
}

# EConfigType (and other app enums) can be embedded in persisted Gson JSON by constant
# name. Preserve enum field names so existing installs remain readable after an R8
# update, while still allowing enum class names and methods to be obfuscated/optimized.
-keep,allowoptimization,allowobfuscation enum com.v2ray.ang.enums.**
-keepclassmembers,allowoptimization enum com.v2ray.ang.enums.** {
    <fields>;
}

-keepattributes Signature,*Annotation*
-keep class * extends com.google.gson.reflect.TypeToken

-keepnames class * extends androidx.work.ListenableWorker
-keepclassmembers class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}

-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator CREATOR;
}

-allowaccessmodification
