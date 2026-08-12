# MobileTina release hardening.
#
# R8 is allowed to optimize, shrink and rename the application. Only contracts whose
# names are consumed reflectively or persisted outside compiled code are kept stable.

# Gson serializes and restores these models reflectively from V2Ray configs,
# subscription records and MMKV. They are persisted across application updates, so R8
# must not remove or rename a field merely because compiled code does not access it.
# The rest of the application remains eligible for shrinking and obfuscation.
-keep class com.v2ray.ang.dto.** {
    *;
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
