package dev.bluehouse.enablevolte

import android.annotation.SuppressLint
import android.app.Activity
import android.app.IActivityManager
import android.app.Instrumentation
import android.content.Context
import android.os.Bundle
import android.system.Os
import android.telephony.CarrierConfigManager
import android.util.Log
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper

const val TAG = "BrokerInstrumentation"

class BrokerInstrumentation : Instrumentation() {
    @SuppressLint("MissingPermission")
    private fun applyConfig(
        subId: Int,
        arguments: Bundle,
    ) {
        Log.i(TAG, "applyConfig")
        val am = IActivityManager.Stub.asInterface(
            ShizukuBinderWrapper(SystemServiceHelper.getSystemService(Context.ACTIVITY_SERVICE)),
        )
        am.startDelegateShellPermissionIdentity(Os.getuid(), null)
        try {
            val configurationManager = this.context.getSystemService(CarrierConfigManager::class.java)
            val overrideValues = toPersistableBundle(arguments)

            try {
                configurationManager.overrideConfig(subId, overrideValues, true)
            } catch (e: SecurityException) {
                if (e.message?.contains("overrideConfig with persistent=true only can be invoked by system app") == true) {
                    configurationManager.overrideConfig(subId, overrideValues, false)
                } else {
                    throw e
                }
            }
        } finally {
            Log.i(TAG, "applyConfig done")
            stopShellIdentity(am)
        }
    }

    @SuppressLint("MissingPermission")
    private fun clearConfig(subId: Int) {
        Log.i(TAG, "clearConfig")
        val am = IActivityManager.Stub.asInterface(
            ShizukuBinderWrapper(SystemServiceHelper.getSystemService(Context.ACTIVITY_SERVICE)),
        )
        am.startDelegateShellPermissionIdentity(Os.getuid(), null)
        try {
            val configurationManager = this.context.getSystemService(CarrierConfigManager::class.java)

            try {
                configurationManager.overrideConfig(subId, null, true)
            } catch (e: SecurityException) {
                if (e.message?.contains("overrideConfig with persistent=true only can be invoked by system app") == true) {
                    configurationManager.overrideConfig(subId, null, false)
                } else {
                    throw e
                }
            }
        } finally {
            Log.i(TAG, "clearConfig done")
            stopShellIdentity(am)
        }
    }

    /**
     * Drops the delegated shell identity.
     *
     * Android 17 QPR2 changed this AIDL method, and calling the old signature
     * throws NoSuchMethodError — after the override has already been applied —
     * which took the whole app down with it (issues #7 and #14). The direct call
     * is tried first, then any method by that name, and a failure is only
     * logged: the activity manager drops the delegation itself when the
     * instrumentation finishes.
     */
    private fun stopShellIdentity(am: IActivityManager) {
        try {
            am.stopDelegateShellPermissionIdentity()
            return
        } catch (e: LinkageError) {
            Log.w(TAG, "stopDelegateShellPermissionIdentity() is gone on this build; trying reflection", e)
        } catch (e: Exception) {
            Log.w(TAG, "stopDelegateShellPermissionIdentity failed", e)
            return
        }
        val method = am.javaClass.methods.firstOrNull { it.name == "stopDelegateShellPermissionIdentity" }
        if (method == null) {
            Log.w(TAG, "No stopDelegateShellPermissionIdentity on this build; relying on instrumentation teardown")
            return
        }
        runCatching {
            val args = method.parameterTypes.map { type ->
                when (type) {
                    Int::class.javaPrimitiveType -> Os.getuid()
                    Long::class.javaPrimitiveType -> 0L
                    Boolean::class.javaPrimitiveType -> false
                    else -> null
                }
            }
            method.invoke(am, *args.toTypedArray())
        }.onFailure { Log.w(TAG, "Reflective stopDelegateShellPermissionIdentity failed", it) }
    }

    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)

        if (arguments == null) {
            finish(Activity.RESULT_CANCELED, Bundle())
            return
        }

        val clear = arguments.getBoolean("moder_clear")
        val subId = arguments.getInt("moder_subId")

        // This runs on the app's own main thread: anything thrown here kills the
        // app, so failures are reported back to the caller instead.
        val result = Bundle()
        val code = try {
            if (clear) {
                this.clearConfig(subId)
            } else {
                this.applyConfig(subId, arguments)
            }
            Activity.RESULT_OK
        } catch (t: Throwable) {
            Log.e(TAG, "Broker failed to apply the carrier config", t)
            result.putString(RESULT_ERROR, t.message ?: t.javaClass.simpleName)
            Activity.RESULT_CANCELED
        }
        finish(code, result)
    }

    companion object {
        const val RESULT_ERROR = "moder_error"
    }
}
