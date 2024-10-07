package jeeves.tools

import io.ktor.client.*
import jeeves.MessageContext
import jeeves.society.Credits
import jeeves.society.Doc
import jeeves.society.ToolModule
import jeeves.society.ToolResponse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import one.wabbit.web.kagi.Kagi

class URLSummarizer(val httpClient: HttpClient, val kagiKey: String): ToolModule<URLSummarizer.Request>(Request.serializer()) {
    override val toolName: String = "URL Summarizer"
    override val description: String = """
        Summarize the content of a URL using the `summarize_url` function. 
        This is a very expensive operation and should be used only if a user explicitly requests it.
    """.trimIndent()
    override suspend fun contextualDescription(context: MessageContext): String = description

    @Serializable sealed interface Request {
        @Doc("Summarize the content of a URL.")
        @SerialName("SummarizeUrl")
        @Serializable data class SummarizeUrl(
            val url: String,
            val summaryType: Kagi.SummaryType): Request
    }

    override suspend fun estimateCost(context: MessageContext, req: Request?): Credits {
        return Credits.fromRealUSD(Kagi.computeCost(Kagi.Model.agnes, 10000))
    }

    override suspend fun execute(context: MessageContext, req: Request): ToolResponse {
        when (req) {
            is Request.SummarizeUrl -> {
                val result = Kagi.execute(req.url, req.summaryType, Kagi.Model.agnes, httpClient, kagiKey)
                return ToolResponse.Success(Json.encodeToJsonElement(result), Credits.fromRealUSD(Kagi.computeCost(Kagi.Model.agnes, result.data.tokens)))
            }
        }
    }
}
