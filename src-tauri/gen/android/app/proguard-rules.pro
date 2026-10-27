# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# Update-Installation (Kanal mobile): Rust meldet das Plugin per Klassennamen an.
-keep class dev.pwiepixel.cubiqlauncher.CubiqMobilePlugin { *; }
-keep class dev.pwiepixel.cubiqlauncher.InstallApkArgs { *; }
-keep class dev.pwiepixel.cubiqlauncher.PushPollArgs { *; }
# Push ohne Verteiler: WorkManager legt den Worker per Name an, nativePoll kommt aus libcubiq_launcher_lib.so.
-keep class dev.pwiepixel.cubiqlauncher.PushPollWorker { *; }
-keep class dev.pwiepixel.cubiqlauncher.PushPollWorker$Companion { *; }
# JNI-Funktion von android-native-keyring-store (Token-Schluessel im Keystore).
-keep class io.crates.keyring.Keyring { *; }
-keep class io.crates.keyring.Keyring$Companion { *; }