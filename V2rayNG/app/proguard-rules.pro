# v2rayNG release hardening
# R8 optimization/minification/resource shrinking is enabled in app/build.gradle.kts.

# Preserve the metadata used by Gson, Kotlin generics and Android reflection.
-keepattributes Signature,*Annotation*,InnerClasses,EnclosingMethod

# Keep JSON field names stable. Many persisted/imported objects are decoded reflectively by Gson;
# classes and executable code may still be renamed/optimized.
-keepclassmembers,allowoptimization class com.v2ray.ang.dto.** {
    <fields>;
}
-keepclassmembers,allowoptimization class com.v2ray.ang.handler.** {
    @com.google.gson.annotations.SerializedName <fields>;
}
-keepclassmembers,allowoptimization class ** {
    @com.google.gson.annotations.SerializedName <fields>;
}

# libv2ray is a native-backed AAR. Its Java/Kotlin API names can participate in JNI lookups,
# therefore keep this narrow boundary while allowing the application code around it to obfuscate.
-keep class libv2ray.** { *; }

# Keep WebView JavaScript entry points when present.
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# WorkManager may instantiate workers by class name from its database.
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# Make stack traces reveal less source-layout information in production.
-renamesourcefileattribute SourceFile

# Let the optimizer collapse visibility/package boundaries when safe.
-allowaccessmodification
-adaptclassstrings
