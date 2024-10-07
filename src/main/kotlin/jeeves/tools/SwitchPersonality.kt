package jeeves.tools

import jeeves.Butler
import jeeves.MessageContext
import jeeves.society.Credits
import jeeves.society.ToolModule
import jeeves.society.ToolResponse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import jeeves.society.Doc
import kotlinx.serialization.json.JsonArray

class SwitchPersonality(val butler: Butler) : ToolModule<SwitchPersonality.Request>(Request.serializer()) {
    override val toolName: String = "Switch Agent"
    override val description: String = "Switch to a different agent."

    override suspend fun contextualDescription(context: MessageContext): String {
        val agents = butler.availableAgents.values.toList()
        val agentList = agents.map {
            val r = " - **${it.name}**: ${it.shortDescription}"
            println("Agent: ${it.name}, size: ${r.length}")
            r
        }
        val agentListStr =
            if (agentList.isNotEmpty()) "\n" + agentList.joinToString("\n")
            else "No agents available."

        return """
                Use `switch_agent` to switch to a different agent (personality).
                Available agents:
                {{agents}}
            """.trimIndent().replace("{{agents}}", agentListStr)
    }

    @Serializable sealed interface Request {
        @Doc("Switch to a different personality.")
        @SerialName("SwitchPersonality")
        @Serializable data class SwitchPersonality(val name: String) : Request

        @Doc("List available personalities.")
        @SerialName("ListPersonalities")
        @Serializable object ListPersonalities : Request
    }

    override suspend fun estimateCost(context: MessageContext, req: Request?): Credits {
        return Credits.MinToolCost
    }

    override suspend fun execute(context: MessageContext, req: Request): ToolResponse {
        when (req) {
            is Request.SwitchPersonality -> {
                val agentName = req.name
                val agent = butler.availableAgents[agentName] ?: error("Agent not found: $agentName")
                butler.channelPersonality[context.primaryEvent.message.channelId.value] = agentName
//                context.primaryEvent.getGuildOrNull()?.let { guild ->
//                    guild.editSelfNickname(agentName)
//                }
                return ToolResponse.Success(
                    JsonObject(mapOf(
                    "message" to JsonPrimitive("Switched to agent: $agentName")
                    )),
                    cost = Credits.MinToolCost)
            }
            is Request.ListPersonalities -> {
                return ToolResponse.Success(
                    JsonObject(mapOf(
                    "message" to JsonArray(
                        butler.availableAgents.values.map {
                            JsonObject(mapOf(
                                "name" to JsonPrimitive(it.name),
                                "description" to JsonPrimitive(it.shortDescription)
                            ))
                        }
                    )
                    )),
                    cost = Credits.MinToolCost)
            }
        }
    }
}
