package com.gabot.pcclient

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class ApplicationLog private constructor(
    val path: Path,
    val warning: String?
) {
    @Synchronized
    fun append(message: String): Boolean = runCatching {
        Files.writeString(
            path,
            "${TIMESTAMP_FORMAT.format(LocalDateTime.now())} $message${System.lineSeparator()}",
            StandardOpenOption.CREATE,
            StandardOpenOption.APPEND
        )
    }.isSuccess

    companion object {
        const val FILE_NAME = "gabot-pc-client.log"
        private val SYSTEM_LOG_PATH = Path.of("/var/log", FILE_NAME)
        private val TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")

        fun create(
            applicationPath: String? = System.getProperty("jpackage.app-path"),
            workingDirectory: Path = Path.of(System.getProperty("user.dir"))
        ): ApplicationLog {
            val requestedPath = resolvePath(applicationPath, workingDirectory)
            if (canAppend(requestedPath)) {
                return ApplicationLog(requestedPath, warning = null)
            }

            val fallbackPath = workingDirectory.resolve(FILE_NAME).toAbsolutePath().normalize()
            val warning = "Cannot write $requestedPath; using $fallbackPath"
            return ApplicationLog(fallbackPath, warning)
        }

        fun resolvePath(applicationPath: String?, workingDirectory: Path): Path {
            val normalizedApplicationPath = applicationPath
                ?.takeIf { it.isNotBlank() }
                ?.let(Path::of)
                ?.toAbsolutePath()
                ?.normalize()

            if (normalizedApplicationPath != null && isSystemInstallation(normalizedApplicationPath)) {
                return SYSTEM_LOG_PATH
            }

            return normalizedApplicationPath
                ?.parent
                ?.resolve(FILE_NAME)
                ?: workingDirectory.resolve(FILE_NAME).toAbsolutePath().normalize()
        }

        private fun isSystemInstallation(applicationPath: Path): Boolean {
            return applicationPath.startsWith("/usr") || applicationPath.startsWith("/opt")
        }

        private fun canAppend(path: Path): Boolean = runCatching {
            path.parent?.let(Files::createDirectories)
            Files.writeString(
                path,
                "",
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND
            )
        }.isSuccess
    }
}
