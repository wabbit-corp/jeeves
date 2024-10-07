package jeeves.tools

import com.aallam.openai.api.image.ImageCreation
import com.aallam.openai.api.image.ImageSize
import com.aallam.openai.api.image.Quality
import com.aallam.openai.api.model.ModelId
import com.aallam.openai.client.OpenAI
import jeeves.Downloader
import jeeves.MessageContext
import jeeves.OpenAIPricing
import jeeves.society.Credits
import jeeves.society.ToolModule
import jeeves.society.ToolResponse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import jeeves.society.Doc


class ImageGeneration(val openAI: OpenAI, val downloader: Downloader) : ToolModule<ImageGeneration.Request>(Request.serializer()) {
    override val toolName: String = "Image Generation"
    override val description: String = """
        You can use the "generate_image" function to generate an image from a prompt. 
        Attach the resulting image to the `send_message`'s attachments.
        The image generator may censor and revise the prompt.
    """.trimIndent()
    override suspend fun contextualDescription(context: MessageContext): String = description

    @Serializable sealed interface Request {
        @Doc("Generate an image from a prompt.")
        @SerialName("GenerateImage")
        @Serializable data class GenerateImage(
            val prompt: String): Request
    }

    override suspend fun estimateCost(context: MessageContext, req: Request?): Credits {
        return Credits.fromRealUSD(0.005)
    }

    override suspend fun execute(context: MessageContext, req: Request): ToolResponse {
        when (req) {
            is Request.GenerateImage -> {
                val prompt = req.prompt
                return generateImage(context.userId, prompt)
            }
        }
    }

    private suspend fun generateImage(user: String, prompt: String): ToolResponse {
        val modelId = ModelId("dall-e-3")
        val imageSize = ImageSize("1024x1024")
        val quality = Quality("standard")

        val response = openAI.imageURL(
            ImageCreation(
                prompt = prompt,
                size = imageSize,
                model = modelId,
                quality = quality,
                n = 1,
                user = user
            )
        )

        val url = response[0].url
        val revisedPrompt = response[0].revisedPrompt
        val contentType = "image/png"

        val downloadedId = downloader.download(url, providedContentType = contentType)

        val cost = OpenAIPricing.dalleCost(modelId, imageSize, quality)

        return ToolResponse.Success(JsonObject(mapOf(
            "generated_local_file_path" to JsonPrimitive("/local/${downloadedId.value}"),
            "your_prompt" to JsonPrimitive(prompt),
            "censored_and_revised_prompt" to JsonPrimitive(revisedPrompt)
        )), cost = cost)
    }

//    private suspend fun generateImage(user: String, prompt: String): Tool.Response {
//        val response = openAI.imageURL(
//            ImageVariation(
//                image = fileSource {
//                    name = "prompt.txt"
//                    source = prompt.byteInputStream().source()
//                },
//                size = ImageSize("1024x1024"),
//                model = ModelId("dall-e-3"),
//                n = 1,
//                user = user
//            )
//        )
//
//        return Tool.Response(JsonObject(mapOf(
//            "image" to JsonPrimitive(response[0].url),
//            "revised_prompt" to JsonPrimitive(response[0].revisedPrompt)
//        )))
//    }
}
