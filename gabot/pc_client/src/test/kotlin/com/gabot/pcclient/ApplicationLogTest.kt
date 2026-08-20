package com.gabot.pcclient

import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ApplicationLogTest {
    @Test
    fun `system installation uses var log`() {
        assertEquals(
            Path.of("/var/log/gabot-pc-client.log"),
            ApplicationLog.resolvePath("/usr/bin/GabotPcClient", Path.of("/tmp"))
        )
        assertEquals(
            Path.of("/var/log/gabot-pc-client.log"),
            ApplicationLog.resolvePath("/opt/gabotpcclient/bin/GabotPcClient", Path.of("/tmp"))
        )
    }

    @Test
    fun `user installation writes beside launcher`() {
        assertEquals(
            Path.of("/home/test/apps/gabot-pc-client.log"),
            ApplicationLog.resolvePath(
                "/home/test/apps/GabotPcClient",
                Path.of("/home/test/work")
            )
        )
    }

    @Test
    fun `development run writes to working directory`() {
        assertEquals(
            Path.of("/home/test/project/gabot-pc-client.log"),
            ApplicationLog.resolvePath(null, Path.of("/home/test/project"))
        )
    }

    @Test
    fun `log appends timestamped message to file`() {
        val logDirectory = createTempDirectory("gabot-pc-log-test")
        val log = ApplicationLog.create(applicationPath = null, workingDirectory = logDirectory)

        assertTrue(log.append("TX: version"))
        assertContains(log.path.readText(), " TX: version")
    }
}
