# ------------------------------------------------------------------------------
# 1. TENSORFLOW LITE PROTECTION
# ------------------------------------------------------------------------------
-keep class org.tensorflow.lite.** { *; }
-keep class org.tensorflow.lite.gpu.** { *; }
-keep class org.tensorflow.lite.support.** { *; }
-keep class org.tensorflow.lite.task.** { *; }
-keep class org.tensorflow.lite.metadata.** { *; }
-keep class org.tensorflow.lite.flex.** { *; }
-keep class org.tensorflow.lite.schema.** { *; }

# Tell R8 to ignore missing internal GPU classes that TFLite handles natively
-dontwarn org.tensorflow.lite.**