# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in android-sdk/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

-dontobfuscate
#-renamesourcefileattribute SourceFile
#-keepattributes SourceFile,LineNumberTable

# shiroikuma-termux (Phase 4): classes the resource XML instantiates by name.
-keep public class com.termux.shiroikuma.ui.ShiroikumaRootView { <init>(...); }
-keep public class com.termux.shiroikuma.ui.** extends androidx.preference.Preference { <init>(...); }

# shiroikuma-termux (Phase 4b): Commons Compress references three optional codecs we never ship.
-dontwarn org.tukaani.**
-dontwarn com.github.luben.**
-dontwarn org.brotli.**
# The automation door and the backup service are instantiated by the framework from the manifest.
-keep public class com.termux.shiroikuma.automation.** { <init>(...); }

# shiroikuma-termux (Phase 4c): the absorbed Termux:Boot / Termux:Widget / Termux:Float packages —
# manifest components and the layout-inflated TermuxFloatView (same belt-and-braces as above).
-keep public class com.termux.boot.** { <init>(...); }
-keep public class com.termux.widget.** { <init>(...); }
-keep public class com.termux.window.** { <init>(...); }
