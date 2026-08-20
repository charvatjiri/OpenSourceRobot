package com.gabot.pcclient

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class ApplicationLog private constructor(
    val path: Path,
    val warning: String?,
    private val maxFileSizeBytes: Long,
    private val maxArchives: Int
) {
    @Synchronized
    fun append(message: String): Boolean = runCatching {
        val line = "${TIMESTAMP_FORMAT.format(LocalDateTime.now())} $message${System.lineSeparator()}"
        rotateIfNeeded(line.toByteArray(Charsets.UTF_8).size.toLong())
        Files.writeString(
            path,
            line,
            StandardOpenOption.CREATE,
            StandardOpenOption.APPEND
        )
    }.isSuccess

    private fun rotateIfNeeded(incomingSizeBytes: Long) {
        if (!Files.exists(path) || Files.size(path) + incomingSizeBytes <= maxFileSizeBytes) {
            return
        }

        if (maxArchives > 0) {
            for (index in maxArchives downTo 2) {
                val source = archivePath(index - 1)
                if (Files.exists(source)) {
                    Files.copy(source, archivePath(index), StandardCopyOption.REPLACE_EXISTING)
                }
            }
            Files.copy(path, archivePath(1), StandardCopyOption.REPLACE_EXISTING)
        }

        Files.writeString(
            path,
            "",
            StandardOpenOption.TRUNCATE_EXISTING,
            StandardOpenOption.WRITE
        )
    }

    private fun archivePath(index: Int): Path = path.resolveSibling("${path.fileName}.$index")

    companion object {
        const val FILE_NAME = "gabot-pc-client.log"
        const val DEFAULT_MAX_FILE_SIZE_BYTES = 5L * 1024 * 1024
        const val DEFAULT_MAX_ARCHIVES = 5
        private val SYSTEM_LOG_PATH = Path.of("/var/log", FILE_NAME)
        private val TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")

        fun create(
            applicationPath: String? = System.getProperty("jpackage.app-path"),
            workingDirectory: Path = Path.of(System.getProperty("user.dir")),
            maxFileSizeBytes: Long = DEFAULT_MAX_FILE_SIZE_BYTES,
            maxArchives: Int = DEFAULT_MAX_ARCHIVES
        ): ApplicationLog {
            require(maxFileSizeBytes > 0) { "Maximum log file size must be positive" }
            require(maxArchives >= 0) { "Archive count cannot be negative" }

            val requestedPath = resolvePath(applicationPath, workingDirectory)
            if (canAppend(requestedPath) && canRotate(requestedPath, maxArchives)) {
                return ApplicationLog(
                    path = requestedPath,
                    warning = null,
                    maxFileSizeBytes = maxFileSizeBytes,
                    maxArchives = maxArchives
                )
            }

            val fallbackPath = workingDirectory.resolve(FILE_NAME).toAbsolutePath().normalize()
            val warning = "Cannot write $requestedPath; using $fallbackPath"
            return ApplicationLog(fallbackPath, warning, maxFileSizeBytes, maxArchives)
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

        private fun canRotate(path: Path, maxArchives: Int): Boolean {
            if (maxArchives == 0 || path.parent?.let(Files::isWritable) == true) {
                return true
            }

            return (1..maxArchives).all { index ->
                val archive = path.resolveSibling("${path.fileName}.$index")
                Files.isRegularFile(archive) && Files.isWritable(archive)
            }
        }
    }
}
