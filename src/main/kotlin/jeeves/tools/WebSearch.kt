package jeeves.tools

import io.ktor.client.*
import jeeves.society.Credits
import jeeves.MessageContext
import jeeves.society.ToolModule
import jeeves.society.ToolResponse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement
import jeeves.society.Doc
import one.wabbit.web.brave.braveSearch

class WebSearch(val httpClient: HttpClient, val subscriptionToken: String) : ToolModule<WebSearch.Request>(Request.serializer()) {
    override val toolName: String = "Web Search"
    override val description: String = "Search the web for information."
    override suspend fun contextualDescription(context: MessageContext) = description

    @Serializable sealed interface Request {
        @Doc("Search the web for information.")
        @SerialName("SearchWeb")
        @Serializable data class SearchWeb(val query: String): Request
    }

    override suspend fun estimateCost(context: MessageContext, req: Request?): Credits =
        Credits.fromRealUSD(0.005)

    override suspend fun execute(context: MessageContext, req: Request): ToolResponse {
        when (req) {
            is Request.SearchWeb -> {
                val result = Json.encodeToJsonElement(braveSearch(httpClient, req.query, subscriptionToken)) as JsonObject
                return ToolResponse.Success(result, cost = Credits.fromRealUSD(0.005))
            }
        }
    }
}
