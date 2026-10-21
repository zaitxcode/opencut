# ---------------------------------------------------------------------------
# OpenCut release rules (R8: shrink, optimise, obfuscate)
# ---------------------------------------------------------------------------

# Metadata that reflection, Compose and kotlinx.serialization rely on.
-keepattributes *Annotation*, InnerClasses, EnclosingMethod, Signature, Exceptions

# kotlinx.serialization: generated serializers are looked up through the
# companion object, so both the companion and the serializer must survive.
-keepclassmembers class com.zaitxcode.opencut.** {
    *** Companion;
}
-keepclasseswithmembers class com.zaitxcode.opencut.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.zaitxcode.opencut.**$$serializer {
    *;
}

# The application, activities, services and providers are kept by the manifest.
-keep class com.zaitxcode.opencut.OpenCutApp { *; }

# Coroutines internals are safe to leave to the library's own rules.
-dontwarn org.jetbrains.annotations.**
-dontwarn kotlinx.serialization.**

# Keep line numbers so a release crash report is still readable, but hide the
# original file names.
-keepattributes SourceFile, LineNumberTable
-renamesourcefileattribute SourceFile

# Treat warnings as warnings: the build must not fail on optional references.
-ignorewarnings
