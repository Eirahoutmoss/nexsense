# Compose kendi kurallarını getirir.
# Hata kaydında okunur yığın izi için satır numaralarını koru.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# kotlinx.serialization: @Serializable sınıfların ürettiği serializer'lar korunmalı.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.eirahoutmoss.nexsense.core.**$$serializer { *; }
-keepclassmembers class com.eirahoutmoss.nexsense.core.** { *** Companion; }
-keepclasseswithmembers class com.eirahoutmoss.nexsense.core.** { kotlinx.serialization.KSerializer serializer(...); }
