package jeeves.tools

import io.ktor.http.*
import io.ktor.util.*
import io.ktor.utils.io.core.*
import jeeves.Downloader
import jeeves.MessageContext
import jeeves.society.Credits
import jeeves.society.ResponseImage
import jeeves.society.ToolModule
import jeeves.society.ToolResponse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import jeeves.society.Doc
import org.apache.tika.Tika
import org.apache.tika.metadata.Metadata

class ReadingAttachments(val downloader: Downloader) : ToolModule<ReadingAttachments.Request>(Request.serializer()) {
    override val toolName: String = "Attachment Reading"
    override val description: String = "You can read some attached documents by using the `get_attachment` function."
    override suspend fun contextualDescription(context: MessageContext): String = description

    @Serializable sealed interface Request {
        @Doc("Read an attached document.")
        @SerialName("GetAttachment")
        @Serializable data class GetAttachment(val url: String) : Request
    }

    override suspend fun estimateCost(context: MessageContext, req: Request?): Credits {
        return Credits.MinToolCost
    }

    private val tika = Tika()

    override suspend fun execute(context: MessageContext, req: Request): ToolResponse {
        when (req) {
            is Request.GetAttachment -> {
                val url = req.url

                val downloadedId = downloader.download(url, precheck = {
                    if (it.status != HttpStatusCode.OK) {
                        error("Failed to download attachment: ${it.status}")
                    }
                    val length = it.contentLength() ?: error("Content length is missing")
                    if (length > 20_000_000) {
                        error("Attachment is too large")
                    }
                })

                val file = downloader.getFileInfo(downloadedId)!!

                when {
                    file.contentType == null -> {
                        error("Content type is missing")
                    }

                    // Text
                    file.contentType == "text" -> {
                        var content = file.file.readText().trim()
                        var hadToCut: Boolean = false

                        if (content.length > 20000) {
                            content = content.substring(0, 20000)
                            hadToCut = true
                        }

                        val result = mutableMapOf<String, JsonPrimitive>()
                        result["content"] = JsonPrimitive(content)
                        if (hadToCut) {
                            result["note"] = JsonPrimitive("Only the first 20000 characters are shown.")
                        }

                        return ToolResponse.Success(
                            data = JsonObject(result),
                            cost = Credits.MinToolCost
                        )
                    }

                    file.contentType == "image" -> {
                        val content = file.file.readBytes()
                        val base64 = content.encodeBase64()
                        return ToolResponse.Success(
                            data = JsonObject(mapOf()),
                            images = listOf(
                                ResponseImage(url, "data:${file.contentType};base64,$base64")
                            ),
                            cost = Credits.MinToolCost
                        )
                    }


                    else -> {
                        val meta = Metadata()
                        meta.set(Metadata.CONTENT_TYPE, file.contentType)
                        file.file.inputStream().use {
                            val detectedContentType = tika.detect(it, meta)
                            println("Detected content type: $detectedContentType")
                            meta.set(Metadata.CONTENT_TYPE, detectedContentType)
                        }

                        file.file.inputStream().use {
                            var content = tika.parseToString(it, meta)
                            if (content.length > 20000) {
                                content = content.substring(0, 20000)
                            }

                            return ToolResponse.Success(
                                data = JsonObject(mapOf("content" to JsonPrimitive(content))),
                                cost = Credits.MinToolCost
                            )
                        }
                    }
                }
            }
        }
    }
}
