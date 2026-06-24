# kotlinx.serialization keeps generated serializers via @Serializable; the
# default consumer rules from the library cover them, but we keep our wire
# DTOs explicitly since R8 full mode is on.
-keepclassmembers,allowobfuscation class io.raylytics.justmyweather.data.** {
    *** Companion;
}
-keepclasseswithmembers class io.raylytics.justmyweather.data.** {
    kotlinx.serialization.KSerializer serializer(...);
}
