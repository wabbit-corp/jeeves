package jeeves

import dev.kord.core.entity.Attachment
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.util.*
import io.ktor.util.cio.*
import io.ktor.utils.io.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory
import std.base.io.tryAtomicCopy
import java.io.File
import java.net.URI
import java.nio.file.Files

@Serializable @JvmInline value class DownloadedFileId(val value: kotlin.ULong)

data class DownloadedFileInfo(
    val id: DownloadedFileId,
    val url: String,
    val size: ULong,
    val sha256: String,
    val extension: String?,
    val fileName: String,
    val provenance: String?,
    val downloadedAt: Long?,
    val headers: Map<String, List<String>>,
    val originalFileName: String?,
    val contentType: String?,
    val lastModifiedAt: String?,
    val etag: String?,
    val file: java.io.File
)

class Downloader(val httpClient: HttpClient, val database: Database, val baseDir: java.io.File) {
    object File : Table() {
        val id = ulong("id").autoIncrement()
        val url = varchar("url", 512)
        val size = ulong("size")
        val sha256 = varchar("sha256", 64)
        val extension = varchar("extension", 16).nullable()
        val fileName = varchar("fileName", 512)
        val provenance = text("provenance").nullable()

        val downloadedAt = long("downloaded_at").nullable()
        val headers = text("headers")

        val originalFileName = varchar("original_file_name", 256).nullable()
        val contentType = varchar("content_type", 64).nullable()
        val lastModifiedAt = varchar("last_modified_at", 256).nullable()
        val etag = varchar("etag", 512).nullable()

        override val primaryKey = PrimaryKey(id)
    }

    object FileDiscordAttachment : Table() {
        val id = ulong("file_id").autoIncrement()
        val attachmentId = ulong("attachment_id")
        override val primaryKey = PrimaryKey(id)
    }

    init {
        transaction(database) {
            SchemaUtils.createMissingTablesAndColumns(File, FileDiscordAttachment)
        }
    }

    suspend fun download(attachment: Attachment, provenance: String? = null): DownloadedFileId {
        val url = attachment.url
        val fileName = attachment.filename
        val contentType = attachment.contentType
        val id = download(url, providedFileName = fileName, providedContentType = contentType) { id ->
            FileDiscordAttachment.insert {
                it[FileDiscordAttachment.id] = id.value
                it[attachmentId] = attachment.id.value
            }
        }
        return id
    }

    fun filenameFromURL(url: String): String {
        val parsed = URI(url)
        return parsed.path.substringAfterLast('/').substringBefore('?')
    }

    private val logger = LoggerFactory.getLogger(this::class.java)

    suspend fun download(
        url: String,
        provenance: String? = null,
        providedFileName: String? = null,
        providedContentType: String? = null,
        precheck: (HttpResponse) -> Unit = { },
        inTransaction: (DownloadedFileId) -> Unit = { }
    ): DownloadedFileId {
        baseDir.mkdirs()

        logger.info("Downloading $url")
        val response = httpClient.get(url)

        precheck(response)

        val contentType = providedContentType ?: response.contentType()?.let {
            "${it.contentType}/${it.contentSubtype}"
        }

        logger.info("Provided content type: $providedContentType, inferred content type: $contentType")

        val contentLength = response.contentLength()
        val lastModifiedAt = response.headers[HttpHeaders.LastModified]
        val etag = response.headers[HttpHeaders.ETag]
        val contentDisposition = response.headers[HttpHeaders.ContentDisposition]

        logger.info("Content length: $contentLength, last modified at: $lastModifiedAt, etag: $etag, content disposition: $contentDisposition")

        val file = withContext(Dispatchers.IO) {
            Files.createTempFile("download-", ".tmp")
        }.toFile()

        try {
            val channel = response.bodyAsChannel()
            channel.copyAndClose(file.writeChannel())

            logger.info("Downloaded to ${file.absolutePath}")

            val sha256 = withContext(Dispatchers.IO) {
                file.inputStream().use {
                    java.security.MessageDigest.getInstance("SHA-256").digest(it.readBytes()).joinToString("") {
                        "%02x".format(it)
                    }
                }
            }

            val contentDispositionFileName = contentDisposition?.let {
                Regex("""filename="(.+?)"""").find(it)?.groupValues?.get(1)
            }
            val contentLocationFileName = response.headers[HttpHeaders.ContentLocation]?.substringAfterLast('/')
            val urlFileName = filenameFromURL(url)

            val fileName = (providedFileName ?: contentDispositionFileName ?: contentLocationFileName ?: urlFileName)
                .let { it.ifBlank { null } }

            logger.info("Provided file name: $providedFileName, content disposition file name: $contentDispositionFileName, content location file name: $contentLocationFileName, url file name: $urlFileName, final file name: $fileName")

            val fileSize = file.length()

            var extension = fileName?.substringAfterLast('.')
            if (extension == fileName) extension = null
            if (extension != null && extension.length > 16) extension = null
            val finalFileName = File(baseDir, "${sha256.take(16)}${extension?.let { ".$it" } ?: ""}")

            logger.info("Final file name: $finalFileName")

            // If there is already a file with the same name, we don't need to copy it again.
            if (!finalFileName.exists()) {
                withContext(Dispatchers.IO) {
                    file.tryAtomicCopy(finalFileName)
                }
            }

            return transaction(database) {
                val id = File.insert {
                    it[File.url] = url
                    it[File.extension] = extension
                    it[File.fileName] = finalFileName.name
                    it[File.size] = fileSize.toULong()
                    it[File.headers] = Json.encodeToString(response.headers.toMap())
                    it[File.sha256] = sha256
                    it[File.originalFileName] = fileName
                    it[File.contentType] = contentType
                    it[File.lastModifiedAt] = lastModifiedAt
                    it[File.downloadedAt] = System.currentTimeMillis()
                    it[File.etag] = etag
                }[File.id]
                DownloadedFileId(id).also { inTransaction(it) }
            }
        } finally {
            if (file.exists()) file.delete()
        }
    }

    fun getFile(id: DownloadedFileId): java.io.File? {
        return transaction(database) {
            val row = File.selectAll().where { File.id eq id.value }.singleOrNull()
            row?.let {
                java.io.File(baseDir, it[File.fileName])
            }
        }
    }

    fun getFileInfo(id: DownloadedFileId): DownloadedFileInfo? {
        return transaction(database) {
            val row = File.selectAll().where { File.id eq id.value }.singleOrNull()
            row?.let {
                DownloadedFileInfo(
                    id = DownloadedFileId(it[File.id]),
                    url = it[File.url],
                    size = it[File.size],
                    sha256 = it[File.sha256],
                    extension = it[File.extension],
                    fileName = it[File.fileName],
                    provenance = it[File.provenance],
                    downloadedAt = it[File.downloadedAt],
                    headers = Json.decodeFromString(it[File.headers]),
                    originalFileName = it[File.originalFileName],
                    contentType = it[File.contentType],
                    lastModifiedAt = it[File.lastModifiedAt],
                    etag = it[File.etag],
                    file = java.io.File(baseDir, it[File.fileName])
                )
            }
        }
    }
}
