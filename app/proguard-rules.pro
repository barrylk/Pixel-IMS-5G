# ---------------------------------------------------------------------------
# Pixel IMS 5G — R8 configuration
#
# This app talks to hidden telephony framework APIs across two privileged
# backends (libsu root process, Shizuku UID 2000). Anything reached by name
# rather than by a direct call must be kept explicitly.
# ---------------------------------------------------------------------------

# Keep readable crash reports. Field-test reports are user-submitted, so
# unobfuscated stack traces are worth far more than the few KB they cost.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# --- Cross-process entry points ---------------------------------------------

# AIDL interface shared with the root process. Both sides must agree on the
# descriptor string, which is derived from the interface name.
-keep interface dev.bluehouse.enablevolte.IPrivilegedService { *; }
-keep class dev.bluehouse.enablevolte.IPrivilegedService$* { *; }

# libsu spawns PrivilegedService in a separate app_process by class name.
-keep class dev.bluehouse.enablevolte.PrivilegedService { *; }
-keep class * extends com.topjohnwu.superuser.ipc.RootService { *; }

# Loaded reflectively via Class.forName in Moder.kt and declared as
# <instrumentation> in the manifest.
-keep class dev.bluehouse.enablevolte.BrokerInstrumentation { *; }

# Config.kt builds these names dynamically: "SIM${slot + 1}IMSStatusQSTileService".
# R8 cannot follow a concatenated name, so keep the tile services explicitly
# rather than relying on the manifest rules alone.
-keep class dev.bluehouse.enablevolte.SIM*QSTileService { *; }
-keep class * extends android.service.quicksettings.TileService { *; }

# --- Hidden API access -------------------------------------------------------

-keep class org.lsposed.hiddenapibypass.** { *; }

# Framework classes resolved by name at runtime. They live in the platform,
# not the APK, but keeping the references stops R8 rewriting call sites that
# reach them through reflection.
-dontwarn android.**
-keep class android.telephony.** { *; }

# Shizuku's binder wrappers are instantiated reflectively by its provider.
-keep class rikka.shizuku.** { *; }
-keep class rikka.sui.** { *; }
-dontwarn rikka.**

# --- Binder / Parcelable -----------------------------------------------------

-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}

# --- Kotlin ------------------------------------------------------------------

-keepclassmembers class **$WhenMappings {
    <fields>;
}
