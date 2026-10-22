package dev.bluehouse.enablevolte

import org.junit.Assert.assertArrayEquals
import org.junit.Test

class RootShellCommandTest {
    @Test
    fun launchesSuThroughAndroidShellForKernelSuExecCompatibility() {
        assertArrayEquals(
            arrayOf("/system/bin/sh", "-c", "exec su"),
            rootShellCommand(),
        )
    }
}
