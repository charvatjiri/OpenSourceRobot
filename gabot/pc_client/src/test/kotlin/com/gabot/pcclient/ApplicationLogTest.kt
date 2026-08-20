package com.gabot.pcclient

import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.io.path.exists
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

    @Test
    fun `log rotates oldest entries into bounded archives`() {
        val logDirectory = createTempDirectory("gabot-pc-log-rotation-test")
        val log = ApplicationLog.create(
            applicationPath = null,
            workingDirectory = logDirectory,
            maxFileSizeBytes = 50,
            maxArchives = 2
        )

        assertTrue(log.append("first-message"))
        assertTrue(log.append("second-message"))
        assertTrue(log.append("third-message"))

        assertContains(log.path.readText(), " third-message")
        assertContains(logDirectory.resolve("${ApplicationLog.FILE_NAME}.1").readText(), " second-message")
        assertContains(logDirectory.resolve("${ApplicationLog.FILE_NAME}.2").readText(), " first-message")
        assertTrue(!logDirectory.resolve("${ApplicationLog.FILE_NAME}.3").exists())
    }
}
