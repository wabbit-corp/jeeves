//package jeeves.tools
//
//import com.aallam.openai.api.chat.FunctionTool
//import com.aallam.openai.api.chat.ToolType
//import com.aallam.openai.api.core.Parameters
//import jeeves.MessageContext
//import kotlinx.serialization.json.JsonArray
//import kotlinx.serialization.json.JsonObject
//import kotlinx.serialization.json.JsonPrimitive
//
//class AskAgent : Tool {
//    override suspend fun name(context: MessageContext): String = "Ask Agent"
//
//    override suspend fun description(context: MessageContext): String {
//        return """
//            You can ask another agent a question. You should choose the best agent for the question you have.
//        """.trimIndent()
//    }
//
//    override val functions: List<ChatTool> = listOf(
//        ChatTool(
//            type = ToolType.Function,
//            function = FunctionTool(
//                name = "ask_agent",
//                description = "Ask another agent a question.",
//                parameters = Parameters(
//                    JsonObject(
//                        mapOf(
//                            "type" to JsonPrimitive("object"),
//                            "properties" to JsonObject(
//                                mapOf(
//                                    "agent" to JsonObject(
//                                        mapOf(
//                                            "type" to JsonPrimitive("string"),
//                                            "description" to JsonPrimitive("The agent to ask the question.")
//                                        )
//                                    ),
//                                    "question" to JsonObject(
//                                        mapOf(
//                                            "type" to JsonPrimitive("string"),
//                                            "description" to JsonPrimitive("The question to ask the agent.")
//                                        )
//                                    )
//                                )
//                            ),
//                            "required" to JsonArray(listOf(JsonPrimitive("agent"), JsonPrimitive("question")))
//                        )
//                    )
//                )
//            )
//        )
//    )
//
//    override suspend fun run(context: MessageContext, name: String, args: JsonObject): Tool.Response {
//
//    }
//}
