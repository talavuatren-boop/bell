# ProGuard / R8 keep rules for HUNG CAM

# Preserve source line numbers for diagnostics
-keepattributes SourceFile,LineNumberTable

# Keep Room entities & DAOs
-keep class com.example.data.** { *; }
-keep class com.example.model.** { *; }

# Keep Kotlin Coroutines & Metadata
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault
-keepclassmembers class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**
