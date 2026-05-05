# SQLCipher JNI
-keep class net.zetetic.** { *; }
-keep class androidx.sqlite.** { *; }

# Argon2kt native bindings
-keep class com.lambdapioneer.argon2kt.** { *; }

# Vosk offline speech recognition JNI/native API
-keep class org.vosk.** { *; }
-keep class org.kaldi.** { *; }
-keep class com.sun.jna.** { *; }
-dontwarn org.vosk.**
-dontwarn org.kaldi.**
-dontwarn com.sun.jna.**

# Hilt / Dagger generated
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.internal.GeneratedComponent { *; }

# Room generated DAOs
-keep class androidx.room.** { *; }
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao class *

# Kotlinx serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Our DataPoint and entities
-keep class com.potpal.mirrortrack.collectors.DataPoint { *; }
-keep class com.potpal.mirrortrack.collectors.ValueType { *; }
-keep class com.potpal.mirrortrack.data.entities.** { *; }
