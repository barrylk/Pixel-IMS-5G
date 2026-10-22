package dev.bluehouse.enablevolte

import java.io.File
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RegionalBindMountTest {
    @get:Rule val temp = TemporaryFolder()

    private fun posix(file: File): String {
        val path = file.absolutePath.replace('\\', '/')
        return if (System.getProperty("os.name").orEmpty().startsWith("Windows"))
            "/" + path[0].lowercaseChar() + path.substring(2) else path
    }

    private fun runBoot(
        disabled: Boolean = false,
        changedFirmware: Boolean = false,
        corruptPatch: Boolean = false,
        mountFails: Boolean = false,
        verifyFails: Boolean = false,
        namespaceFails: Boolean = false,
        remountFails: Boolean = false,
    ): Pair<File, String> {
        val module = temp.newFolder("module")
        val files = File(module, "files").apply { mkdir() }
        val target = File(temp.root, "live-cfg.db").apply { writeText("stock") }
        val source = File(files, "cfg.db").apply { writeText("patched") }
        val backup = File(temp.root, "stock-backup.db").apply { writeText("stock") }
        fun hash(file: File): String = java.security.MessageDigest.getInstance("SHA-256")
            .digest(file.readBytes()).joinToString("") { "%02x".format(it) }
        File(module, "source.sha256").writeText(hash(target))
        File(module, "patched.sha256").writeText(hash(source))
        if (disabled) File(module, "disable").writeText("")
        if (changedFirmware) target.writeText("new firmware")
        if (corruptPatch) source.writeText("corrupt")
        val bin = temp.newFolder("bin")
        val log = File(temp.root, "mount.log")
        // Replace only OS mount/SELinux boundaries. Hashes and fixture files are real.
        File(bin, "readlink").writeText("#!/bin/sh\necho 'mnt:[1]'\n")
        if (namespaceFails) File(bin, "readlink").writeText(
            "#!/bin/sh\ncase \"\$1\" in /proc/self/*) echo 'mnt:[2]';; *) echo 'mnt:[1]';; esac\n",
        )
        File(bin, "nsenter").writeText("#!/bin/sh\nexit 1\n")
        File(bin, "ls").writeText("#!/bin/sh\necho 'u:object_r:vendor_fw_file:s0 file'\n")
        File(bin, "chcon").writeText("#!/bin/sh\nexit 0\n")
        File(bin, "mount").writeText(
            "#!/bin/sh\nprintf '%s\\n' \"\$*\" >> \"\$MOUNT_LOG\"\n" +
                // BusyBox's single-argument remount looks up the mount table. Require
                // explicit source + target so the boot script bypasses that lookup.
                "if [ \"\$1\" = '-o' ] && [ \"\$#\" != 4 ]; then echo 'mount table lookup failed' >&2; exit 1; fi\n" +
                (if (remountFails) "if [ \"\$1\" = '-o' ]; then echo 'permission denied' >&2; exit 1; fi\n" else "") +
                (if (mountFails) "exit 1\n" else if (verifyFails) "exit 0\n" else
                    "if [ \"\$1\" = '--bind' ]; then cp \"\$2\" \"\$3\"; fi\n"),
        )
        File(bin, "umount").writeText(
            "#!/bin/sh\nprintf 'umount %s\\n' \"\$*\" >> \"\$MOUNT_LOG\"\ncp \"\$STOCK_BACKUP\" \"\$1\"\n",
        )
        bin.listFiles()!!.forEach { it.setExecutable(true) }
        val script = File(module, "post-fs-data.sh").apply {
            writeText(regionalSingleFileBootScript(posix(target)))
        }
        val bash = if (System.getProperty("os.name").orEmpty().startsWith("Windows"))
            "C:/Program Files/Git/bin/bash.exe" else "bash"
        val process = ProcessBuilder(
            bash, "-c", "export PATH=\"\$TEST_BIN:/usr/bin:/bin\"; exec /bin/sh \"\$TEST_SCRIPT\"",
        )
            .redirectErrorStream(true).apply {
                environment()["TEST_BIN"] = posix(bin)
                environment()["TEST_SCRIPT"] = posix(script)
                environment()["MOUNT_LOG"] = posix(log)
                environment()["STOCK_BACKUP"] = posix(backup)
                environment()["MSYS_NO_PATHCONV"] = "1"
            }.start()
        assertTrue("Boot script must not stall", process.waitFor(10, TimeUnit.SECONDS))
        val output = process.inputStream.bufferedReader().readText()
        assertEquals(output, 0, process.exitValue())
        return module to if (log.exists()) log.readText() else ""
    }

    @Test fun mountsOnlyDatabaseAfterVerification() {
        val (module, commands) = runBoot()
        assertEquals("active", File(module, "bind.status").readText().trim())
        assertTrue(commands.contains("--bind "))
        assertTrue(commands.contains("remount,bind,ro"))
        assertFalse(commands.contains("overlay"))
    }

    @Test fun disabledModuleNeverMounts() { assertEquals("", runBoot(disabled = true).second) }
    @Test fun firmwareMismatchNeverMounts() {
        val (module, commands) = runBoot(changedFirmware = true)
        assertEquals("", commands)
        assertTrue(File(module, "bind.status").readText().contains("firmware database changed"))
    }
    @Test fun corruptDatabaseNeverMounts() {
        val (module, commands) = runBoot(corruptPatch = true)
        assertEquals("", commands)
        assertTrue(File(module, "bind.status").readText().contains("checksum mismatch"))
    }
    @Test fun wrongNamespaceNeverMounts() {
        val (module, commands) = runBoot(namespaceFails = true)
        assertEquals("", commands)
        assertTrue(File(module, "bind.status").readText().contains("init mount namespace unavailable"))
    }
    @Test fun mountFailureIsReported() {
        val (module, _) = runBoot(mountFails = true)
        val status = File(module, "bind.status").readText()
        assertTrue(status, status.contains("mount failed"))
    }
    @Test fun verificationFailureUnmountsOnlyDatabase() {
        val (module, commands) = runBoot(verifyFails = true)
        assertTrue(File(module, "bind.status").readText(), commands.contains("umount "))
        assertTrue(File(module, "bind.status").readText().contains("verification failed"))
    }

    @Test fun readOnlyFailureRestoresStockInsteadOfLeavingWritablePatch() {
        val (module, commands) = runBoot(remountFails = true)
        assertTrue(commands.contains("umount "))
        assertEquals("stock", File(temp.root, "live-cfg.db").readText())
        assertTrue(File(module, "bind.status").readText().contains("permission denied"))
    }
}
