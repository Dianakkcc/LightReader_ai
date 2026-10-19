# --- Room ---
-keep class androidx.room.** { *; }
-keep class * extends androidx.room.RoomDatabase
-keepclassmembers class * {
    @androidx.room.* <methods>;
}
-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod

# --- PDFBox-Android (reflection heavy) ---
-keep class com.tom_roush.** { *; }
-dontwarn com.tom_roush.**
-dontwarn org.apache.pdfbox.**

# --- kotlinx.serialization ---
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.lightreader.**$$serializer { *; }
-keepclassmembers class com.lightreader.** {
    *** Companion;
}
-keepclasseswithmembers class com.lightreader.** {
    kotlinx.serialization.KSerializer serializer(...);
}
