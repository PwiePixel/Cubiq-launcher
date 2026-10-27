# JNI: libpojavexec/libexithook finden diese Klassen und Methoden per Name.
-keep class net.kdt.pojavlaunch.** { *; }
-keep class org.lwjgl.glfw.CallbackBridge { *; }
-keep class org.lwjgl.glfw.CallbackBridge$* { *; }
-keep class com.oracle.dalvik.VMLauncher { *; }
-keep class dalvik.annotation.optimization.** { *; }
# Tauri-Plugin (Reflection) und Overlay-Provider (per Manifest-Metadaten geladen)
-keep class dev.pwiepixel.cubiq.game.** { *; }
-keep class * implements dev.pwiepixel.cubiq.game.engine.OverlayProvider { <init>(); }
# SDL3-Java-Seite (Minecraft 26.3+): libSDL3 registriert ihre Natives per Klassen-/Methodenname.
-keep class org.libsdl.app.** { *; }
