# ==============================================================================
# FRACTAL PROGUARD & R8 RULES
# ==============================================================================

# ------------------------------------------------------------------------------
# 1. TENSORFLOW LITE PROTECTION
# ------------------------------------------------------------------------------
# Keeps all TFLite classes and their native C++ bindings completely untouched.
-keep class org.tensorflow.lite.** { *; }
-keep class org.tensorflow.lite.gpu.** { *; }
-keep class org.tensorflow.lite.support.** { *; }
-keep class org.tensorflow.lite.task.** { *; }
-keep class org.tensorflow.lite.metadata.** { *; }
-keep class org.tensorflow.lite.flex.** { *; }
-keep class org.tensorflow.lite.schema.** { *; }

# Prevents the native methods from being stripped out
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}

# ------------------------------------------------------------------------------
# 2. GSON & JSON PARSING PROTECTION
# ------------------------------------------------------------------------------
# Keeps the Gson library itself safe
-keep class com.google.gson.** { *; }
-keep class com.google.gson.reflect.** { *; }
-keep interface com.google.gson.annotations.** { *; }

# KEEP TASK CONTAINERS SAFE
# This is crucial! If R8 renames the variables in these classes,
# the JSON from the server will not map correctly and the app will crash.
-keep class AppBackend.TaskContainer.** { *; }
-keep class AppBackend.Network.ModelUpdateTransmission.** { *; }

# ------------------------------------------------------------------------------
# 3. FIREBASE & DATA TRANSFER OBJECTS (DTOs) PROTECTION
# ------------------------------------------------------------------------------
# Keeps your DTOs safe so Firebase can serialize/deserialize them perfectly
-keep class AppBackend.Network.RegisteredInfo.** { *; }
-keep class AppFrontend.Interface.Auth.** { *; }

# Generic Firebase Protections
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }

# ------------------------------------------------------------------------------
# 4. KOTLIN COROUTINES PROTECTION
# ------------------------------------------------------------------------------
# Coroutines use a lot of reflection under the hood that R8 sometimes breaks
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}

# ------------------------------------------------------------------------------
# GENERAL APP STABILITY
# ------------------------------------------------------------------------------
# Keep line numbers in crash reports so you know exactly where a crash happened
# in the Google Play Console!
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Preserve all annotations
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod