package jeeves.tools

import com.aallam.openai.api.chat.ChatCompletionRequest
import com.aallam.openai.api.chat.ChatMessage
import com.aallam.openai.api.model.ModelId
import com.aallam.openai.client.OpenAI
import jeeves.MessageContext
import jeeves.createLongMessage
import jeeves.society.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive
import std.indent

class ComplexProblem(val openAI: OpenAI) : ToolModule<ComplexProblem.Request>(Request.serializer()) {
    @Serializable
    sealed interface Request {
        @Doc("In case you need to solve a very complex problem: a task that requires a lot of reasoning or planning, you can use this tool.")
        @SerialName("SolveComplexProblem")
        @Serializable data class SolveComplexProblem(
            @Doc("Describe the problem you need to solve in the greatest detail you can. At least one or two paragraphs.")
            val problem: String
        ) : Request
    }

    override val toolName: String = "Complex Problem Solver"
    override val description: String = """
            This tool is designed to help you solve complex problems that require a lot of reasoning or planning.
            Use `solve_complex_problem` to describe the problem you need to solve.
        """.trimIndent()

    override suspend fun contextualDescription(context: MessageContext): String = description

    override suspend fun execute(context: MessageContext, req: Request): ToolResponse {
        when (req) {
            is Request.SolveComplexProblem -> {
                val completion = openAI.chatCompletion(
                    ChatCompletionRequest(
                        model = ModelId("o1-preview"),
                        messages = listOf(
                            ChatMessage.User(
                                content = req.problem
                            )
                        )
                    )
                )

                val text = completion.choices.first().message.content!!
                context.primaryEvent.message.channel.createLongMessage(text.indent("> "), emptyList())

                return ToolResponse.Success(
                    data = JsonPrimitive(text),
                    cost = Credits.MinToolCost
                )
            }
        }
    }

    override suspend fun estimateCost(context: MessageContext, req: Request?): Credits = Credits.MinToolCost

}
