package jeeves.tools

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import jeeves.society.Credits
import jeeves.Downloader
import jeeves.MessageContext
import jeeves.society.ToolModule
import jeeves.society.ToolResponse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import one.wabbit.levenshtein.levenshtein
import jeeves.society.Doc


class Imgflip(
    val httpClient: HttpClient,
    val topMemes: List<web.imgflip.Imgflip.Meme>,
    val imgflipUsername: String,
    val imgflipPassword: String,
    val downloader: Downloader
) : ToolModule<Imgflip.Request>(Request.serializer()) {
    override val toolName: String = "Memes"
    override val description: String = "You can generate a meme given a template and text."
    override suspend fun contextualDescription(context: MessageContext): String = description

    @Serializable
    sealed interface Request {
        @Doc("Generate a meme given a template and text.")
        @SerialName("GenerateMeme")
        @Serializable data class GenerateMeme(
            @Doc("The name of the meme template on Imgflip.")
            val templateName: String,
            @Doc("The text to put in each box of the meme.")
            val boxText: List<String>): Request

        @Doc("List all meme templates.")
        @SerialName("ListAllMemes")
        @Serializable data object ListAllMemes: Request
    }

    fun listAllMemes(): ToolResponse {
        return ToolResponse.Success(JsonObject(mapOf(
            "memes" to JsonArray(topMemes.map {
                JsonObject(mapOf(
                    "name" to JsonPrimitive(it.name),
                    "box_count" to JsonPrimitive(it.boxCount)
                ))
            })
        )), cost = Credits.MinToolCost)
    }

    suspend fun generateMeme(templateName: String, boxText: List<String>): ToolResponse {
        val memeId = topMemes.find { it.name.equals(templateName, ignoreCase = true) }?.id

        if (memeId == null) {
            val matches = topMemes.map { it.name to levenshtein(templateName, it.name) }
                .sortedBy { it.second }
                .take(10)
                .joinToString(", ") { it.first }

            return ToolResponse.Success(
                JsonObject(
                    mapOf(
                        "error" to JsonPrimitive("Meme template \"$templateName\" not found. Closest matches: $matches")
                    )
                ),
                cost = Credits.MinToolCost
            )
        }

        val data = mutableMapOf(
            "template_id" to memeId,
            "username" to imgflipUsername,
            "password" to imgflipPassword
        )

        for ((i, text) in boxText.withIndex()) {
            data["boxes[$i][text]"] = text
        }

        val response = httpClient.submitForm(
            url = "https://api.imgflip.com/caption_image",
            formParameters = parameters {
                data.forEach { (k, v) -> append(k, v) }
            }
        )

        val bodyAsText = response.bodyAsText()

        // Example Success Response:
        //{
        //   "success": true,
        //   "data": {
        //      "url": "https://i.imgflip.com/123abc.jpg",
        //      "page_url": "https://imgflip.com/i/123abc"
        //   }
        //}
        //
        //Example Failure Response:
        //{
        //   "success" => false,
        //   "error_message" => "Some hopefully-useful statement about why it failed"
        //}

        println(bodyAsText)

        val json = Json.decodeFromString<JsonObject>(bodyAsText)

        if (!json["success"]!!.jsonPrimitive.boolean) {
            return ToolResponse.Success(
                JsonObject(
                    mapOf(
                        "error" to JsonPrimitive(json["error_message"]!!.jsonPrimitive.content)
                    )
                ),
                cost = Credits.MinToolCost
            )
        }

        val url = json["data"]!!.jsonObject["url"]!!.jsonPrimitive.content
        val urlExtension = url.substringAfterLast('.')
        val contentType = when (urlExtension) {
            "jpg" -> "image/jpeg"
            "png" -> "image/png"
            else -> "image/jpeg"
        }

        val downloadedId = downloader.download(url, providedContentType = contentType)

        return ToolResponse.Success(
            JsonObject(mapOf("image_file_id" to JsonPrimitive("/local/${downloadedId.value}"))),
            cost = Credits.MinToolCost
        )
    }

    override suspend fun estimateCost(context: MessageContext, req: Request?): Credits =
        Credits.MinToolCost

    override suspend fun execute(context: MessageContext, req: Request): ToolResponse {
        when (req) {
            Request.ListAllMemes -> return listAllMemes()
            is Request.GenerateMeme -> {
                val templateName = req.templateName
                val boxText = req.boxText
                return generateMeme(templateName, boxText)
            }
        }
    }

    companion object {
        suspend fun create(httpClient: HttpClient, imgflipUsername: String, imgflipPassword: String, downloader: Downloader): Imgflip {
            val response = httpClient.get("https://api.imgflip.com/get_memes")
            val bodyAsText = response.bodyAsText()
            val allMemes = Json.decodeFromString<web.imgflip.Imgflip.MemeResponse>(bodyAsText)
            return Imgflip(httpClient, allMemes.data.memes.sortedByDescending { it.captions }, imgflipUsername, imgflipPassword, downloader)
        }
    }
}
