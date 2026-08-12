# MobileTina release hardening.
#
# R8 is allowed to optimize, shrink and rename the application. Only contracts whose
# names are consumed reflectively or persisted outside compiled code are kept stable.

# Gson serializes these fields into V2Ray configs, subscription records and MMKV data.
# Preserve field names for backward-compatible JSON while still allowing class and
# method names to be obfuscated and unused types to be removed.
-keepclassmembers,allowoptimization,allowshrinking class com.v2ray.ang.dto.** {
    <fields>;
}

# Generic signatures and Gson annotations describe JSON types at runtime.
-keepattributes Signature,*Annotation*
-keep class * extends com.google.gson.reflect.TypeToken

# WorkManager persists worker class names in its database. Stable names prevent pending
# expiry and 24-hour-limit work from breaking after an application update.
-keepnames class * extends androidx.work.ListenableWorker
-keepclassmembers class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# JNI entry points must retain the names expected by bundled native libraries.
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}

# Parcelable creators are looked up by the Android framework.
-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator CREATOR;
}
