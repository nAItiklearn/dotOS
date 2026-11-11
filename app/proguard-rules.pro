# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.dotos.**$$serializer { *; }
-keepclassmembers class com.dotos.** { *** Companion; }
-keepclasseswithmembers class com.dotos.** { kotlinx.serialization.KSerializer serializer(...); }

# Services/receivers are referenced from the manifest (kept by AAPT rules). Nothing else to keep.
