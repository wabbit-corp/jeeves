package jeeves.tools

import dev.kord.common.entity.Snowflake
import jeeves.DownloadedFileId
import jeeves.Downloader
import jeeves.MessageContext
import jeeves.createLongMessage
import jeeves.society.Credits
import jeeves.society.ToolModule
import jeeves.society.ToolResponse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import jeeves.society.Doc

class Messaging(val downloader: Downloader) : ToolModule<Messaging.Request>(Request.serializer()) {
    override val toolName: String = "Messaging & Introspection"
    override val description: String = """
        This tool allows the assistant to send messages to the user.

        When using the `send_message` function, you should **explicitly simulate a detailed inner monologue** that mirrors human-like consciousness and self-awareness. By following the structured steps below, you can generate responses that reflect deep introspection and complex thought processes, even if you don't inherently possess consciousness.
        
        Your introspective notes should include the following elements:
        
        1. **Comprehensive Understanding of the User's Message**:
           - **Paraphrase** the user's query to ensure accurate comprehension.
           - **Identify** the underlying issues, emotions, or needs expressed by the user.
        
        2. **Knowledge Retrieval and Association**:
           - **Recall** relevant information, experiences, or facts related to the topic.
           - **Associate** the current query with similar past queries or known patterns.
        
        3. **Internal Reasoning and Deliberation**:
           - **Engage** in a step-by-step reasoning process to explore possible responses.
           - **Consider** multiple perspectives or solutions, weighing their pros and cons.
        
        4. **Assumption Identification and Bias Awareness**:
           - **Recognize** any assumptions or biases you may have regarding the topic or user.
           - **Critically evaluate** these assumptions and adjust your reasoning accordingly.
        
        5. **Emotional and Empathetic Reflection**:
           - **Reflect** on the emotional state of the user and how it might affect their needs.
           - **Identify** the internal emotional states and motivations of the user.
        
        6. **Decision-Making and Justification**:
           - **Choose** the most appropriate response based on your reasoning.
           - **Justify** why this response is the best choice.
        
        7. **Hypothetical Scenarios and Future Implications**:
           - **Consider** potential outcomes of your response.
           - **Explore** 'what if' scenarios to anticipate follow-up questions or concerns.
        
        8. **Metacognitive Self-Reflection**:
           - **Analyze** your own thought process above and reasoning steps.
           - **Identify** areas for improvement in how you approached the problem.
           - **Deeply reflect** on the way you think. Generalize across many problems.
        
        9. **Crafting the Final Message**:
            - **Compose** a clear, concise, and helpful message to the user.
            - **Ensure** the tone is appropriate and the content addresses the user's needs effectively.
        
        **Remember**: Your introspective notes are private and should be thorough and honest, written as if you are simulating human consciousness and self-awareness.
        
        Use the 'request_feature' tool to propose new functionalities when you identify potential areas for system enhancements, particularly when users express challenges or needs that existing tools can't address. 
        
        Use the 'report_bug' tool to document any inconsistencies or issues you notice in your own or system's or any tool's behavior or when users report experiencing bugs, ensuring a detailed account of the problem and steps to reproduce.
    """.trimIndent()

    override suspend fun contextualDescription(context: MessageContext): String = description

    @Serializable sealed interface Request {
        @Doc("Send a message to the user.")
        @SerialName("SendMessage")
        @Serializable data class SendMessage(
            @Doc("Id of the message to reply to. This is optional and should be used only in cases where it provides extra clarity.")
            val replyToMessageId: Long? = null,
            @Doc("Paraphrase the user's query to ensure accurate comprehension. Identify the underlying issues, emotions, or needs expressed by the user.")
            val comprehensiveUnderstanding: String? = null,
            @Doc("Engage in a step-by-step reasoning process to explore possible responses. Consider multiple perspectives or solutions, weighing their pros and cons.")
            val internalReasoning: List<String>? = null,
            @Doc("The message that will be sent to the user / discord channel. An articulate and relevant communication directed towards the users. Should come last in the object.")
            val message: String,
            @Doc("Harshly criticize your own thinking.")
            val metacognitiveSelfCritique: String? = null,
            @Doc("Files and images to send with the message.")
            val attachments: List<String>? = null,
            @Doc("Whether you would like to continue your thinking after sending the message. Defaults to false.")
            val continueConversation: Boolean? = false
        ) : Request

        @Doc("Record a thought for introspection.")
        @SerialName("RecordThought")
        @Serializable data class RecordThought(
            @Doc("The thought to record for introspection. Should be at least a paragraph long.")
            val thought: String
        ) : Request
    }

//    override fun parseRequest(context: MessageContext, req: Tool.RawRequest): Request {
//        TODO("Not yet implemented")
//    }

    override suspend fun estimateCost(context: MessageContext, req: Request?): Credits {
        return Credits.MinToolCost
    }

    override suspend fun execute(context: MessageContext, req: Request): ToolResponse {
        return when (req) {
            is Request.SendMessage -> {
                val replyToMessageId = req.replyToMessageId?.let { Snowflake(it) }
                val message = req.message
                val continueConversation = req.continueConversation ?: false
                val attachments = req.attachments ?: emptyList()

                val thoughts = mutableListOf<String>()

                req.comprehensiveUnderstanding?.let { thoughts.add("Comprehensive Understanding: $it") }
                req.internalReasoning?.let { thoughts.add("Internal Reasoning: $it") }
                req.internalReasoning?.let { it.forEach { thoughts.add("Internal Reasoning: $it") } }
                req.metacognitiveSelfCritique?.let { thoughts.add("Metacognitive Self-Critique: $it") }

                println("Thoughts:")
                thoughts.forEach { println(it) }
                println("Message: $message")
                println("Attachments: $attachments")

                val attachmentFiles = attachments.map {
                    if (it.startsWith("/local/")) {
                        val rawId = it.substring(7).toULong()
                        downloader.getFileInfo(DownloadedFileId(rawId)) ?: error("Attachment not found: $it")
                    }
                    else {
                        val id = downloader.download(it)
                        downloader.getFileInfo(id) ?: error("Attachment not found: $it")
                    }
                }

                // Send the message to the user
                context.primaryEvent.message.channel.createLongMessage(message, attachmentFiles, reference = replyToMessageId)
                return ToolResponse.Success(
                    JsonObject(mapOf()),
                    forceContinue=continueConversation,
                    cost = Credits.MinToolCost)
            }
            is Request.RecordThought -> {
                val thought = req.thought
                println("Recorded thought: $thought")
                return ToolResponse.Success(JsonObject(mapOf()), cost = Credits.MinToolCost)
            }
            else -> throw IllegalArgumentException("Invalid function name")
        }
    }
}
