package dev.bluehouse.enablevolte

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import java.io.File

/**
 * What the app can tell about root on this device, read passively.
 *
 * Nothing here asks for root or runs `su`. It only looks at facts already
 * visible to a normal app — which su managers are installed, whether a `su`
 * binary sits on a world-readable path, the build tags — plus whether the app's
 * own tightly-scoped UID 0 service happens to be connected. It is for the Home
 * screen to say "this device is rooted", not a gate on anything.
 */
data class RootStatus(
    val rooted: Boolean,
    val managerName: String?,
    val serviceActive: Boolean,
    val evidence: List<String>,
)

object RootDetector {
    // Common su-manager packages. Declared in <queries> so they stay visible on
    // Android 11+; an undeclared package simply reads as "not installed".
    private val MANAGERS =
        linkedMapOf(
            "com.topjohnwu.magisk" to "Magisk",
            "io.github.huskydg.magisk" to "Magisk (Delta)",
            "io.github.vvb2060.magisk" to "Magisk (alt)",
            "me.weishu.kernelsu" to "KernelSU",
            "me.bmax.apatch" to "APatch",
        )

    private val SU_PATHS =
        listOf(
            "/sbin/su",
            "/system/bin/su",
            "/system/xbin/su",
            "/system/sbin/su",
            "/vendor/bin/su",
            "/su/bin/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/data/local/su",
            "/system/bin/.ext/.su",
        )

    fun detect(context: Context): RootStatus {
        val evidence = mutableListOf<String>()
        var manager: String? = null

        val pm = context.packageManager
        for ((pkg, name) in MANAGERS) {
            if (isInstalled(pm, pkg)) {
                if (manager == null) manager = name
                evidence += "$name installed"
            }
        }

        SU_PATHS.firstOrNull { path -> runCatching { File(path).exists() }.getOrDefault(false) }
            ?.let { evidence += "su binary at $it" }

        if (Build.TAGS?.contains("test-keys") == true) {
            evidence += "test-keys build"
        }

        val serviceActive =
            PrivilegeManager.activeMode == PrivilegeMode.ROOT && PrivilegeManager.isRootReady()
        if (serviceActive) {
            evidence += "root service connected as UID 0"
        }

        return RootStatus(
            rooted = evidence.isNotEmpty(),
            managerName = manager,
            serviceActive = serviceActive,
            evidence = evidence,
        )
    }

    private fun isInstalled(pm: PackageManager, pkg: String): Boolean =
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(pkg, 0)
            }
            true
        }.getOrDefault(false)
}
