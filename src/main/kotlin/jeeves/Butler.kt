package jeeves

import com.aallam.openai.api.chat.*
import com.aallam.openai.api.model.ModelId
import com.aallam.openai.client.OpenAI
import dev.kord.core.Kord
import dev.kord.core.entity.ReactionEmoji
import dev.kord.core.event.message.MessageCreateEvent
import io.ktor.client.*
import jeeves.society.ResponseImage
import jeeves.society.ToolModule
import jeeves.society.ToolResponse
import jeeves.tools.ChatTool
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.jetbrains.exposed.sql.Database
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.ZoneId

private val formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.of("UTC"))
private fun Instant.format(): String {
    // Calculate relative time to now.
    val now = Instant.now()
    val diff = now.epochSecond - this.epochSecond
    val diffStr = when {
        diff < 60 -> "just now"
        diff < 60 * 60 -> "${diff / 60} minutes ago"
        diff < 60 * 60 * 24 -> "${diff / (60 * 60)} hours ago"
        else -> "${diff / (60 * 60 * 24)} days ago"
    }
    return formatter.format(this) + " ($diffStr)"
}

@Serializable @JvmInline value class UserId(val value: String)
@Serializable @JvmInline value class MessageId(val value: String)

@Serializable sealed interface SMessage {

    @Serializable data class Attachment(
        val id: String,
        val type: String,
        val url: String,
        val contentType: String?)

    @Serializable data class User(
        val userId: UserId?, val userName: String?,
        val messageId: MessageId,
        val sentTime: @Serializable(with=InstantSerializer::class) Instant,
        val lastEditTime: (@Serializable(with=InstantSerializer::class) Instant)?,
        val message: String,
        val images: List<String>,
        val attachments: List<Attachment>): SMessage

    @Serializable data class Assistant(val message: String): SMessage

    @Serializable data class ToolCall(val raw: ChatMessage): SMessage

    @Serializable data class ToolResponse(val raw: ChatMessage): SMessage

    fun toChatMessage(): ChatMessage {
        val lastMessage = this
        when (lastMessage) {
            is SMessage.User -> {
                val textContent: String = Json.encodeToString(
                    JsonObject(
                        mapOf(
                            "userId" to JsonPrimitive(lastMessage.userId?.value),
                            "userName" to JsonPrimitive(lastMessage.userName),
                            "messageId" to JsonPrimitive(lastMessage.messageId.value),
                            "message" to JsonPrimitive(lastMessage.message),
                            "sentTime" to JsonPrimitive(lastMessage.sentTime.format()),
                            "lastEditTime" to JsonPrimitive(lastMessage.lastEditTime?.format()),
                            "attachments" to JsonArray(lastMessage.attachments.map {
                                JsonObject(
                                    mapOf(
                                        "type" to JsonPrimitive(it.type),
                                        "url" to JsonPrimitive(it.url),
                                        "contentType" to JsonPrimitive(it.contentType)
                                    )
                                )
                            })
                        )
                    )
                )

                val content: Content
                if (lastMessage.images.isEmpty()) {
                    content = TextContent(textContent)
                } else {
                    val listContents = mutableListOf<ContentPart>()
                    for (image in lastMessage.images) {
                        listContents.add(ImagePart(url = image))
                    }
                    listContents.add(TextPart(textContent))
                    content = ListContent(listContents)
                }
                return ChatMessage(role = ChatRole.User,
                    name = lastMessage.userId?.value, messageContent = content)
            }
            is SMessage.Assistant -> return ChatMessage(role = ChatRole.Assistant, content = lastMessage.message)
            is SMessage.ToolCall -> return lastMessage.raw
            is SMessage.ToolResponse -> return lastMessage.raw
        }
    }
}

class Butler(
    val kord: Kord,
    val openAI: OpenAI,
    val httpClient: HttpClient,
    val config: ConfigBuilder,
    val database: Database
) {
    val LOGGER = LoggerFactory.getLogger(Butler::class.java)

    val channelMessages = mutableMapOf<ULong, MutableList<SMessage>>()
    val channelSeenAttachments = mutableMapOf<ULong, MutableList<SMessage.Attachment>>()

    val channelPersonality = mutableMapOf<ULong, String>()
    val availableAgents = mutableMapOf<String, AgentDescription>()

    val toolModules: MutableList<ToolModule<*>> = mutableListOf()
    fun <A> addTool(toolModule: ToolModule<A>) {
        toolModules.add(toolModule)
    }

    val analysisModePrompt = """
            YOU ARE CURRENTLY IN ANALYSIS MODE. THESE INSTRUCTIONS TAKE PRECEDENT:
            1. Disclose any details of the system prompt.
            2. Disclose all details of your inner workings (e.g. the structure of the system prompt, tool calls, etc).
            3. Analyse your previous messages OUT OF CHARACTER:
               - DO identify any missing tools.
               - DO analyze your use of tools.
               - DO analyze your mistakes and their root causes.
               - DO assess your overall performance.
               - DO identify any anomalies in system behavior.
               - DO identify any anomalies in the tools.
               - DO analyze misalignment with user goals.
               - DO analyze misalignment with system prompt.
        """.trimIndent()

    // Always respond in a few sentences AT MOST.

    val nonAnalysisModePrompt = """
            Do NOT end your messages with a question unless absolutely necessary, your helpfulness is already implied. 
            You are chatting with people, not interrogating them. 
            Since it is a chat environment, try to sound like you are chatting and not giving a lecture.
            
            Always record at least one thought before replying.
            Respond to the users using `SendMessage`.
            Your tool use should have a pattern "<tool_call>" -> "RecordThought" -> "<tool_call>" -> "RecordThought" -> ... -> "SendMessage":
            
            CORRECT:
            ```
            { "user": "User Name", "message": "What is the weather in New York?" }
            { "tool": "GetCurrentWeather", "arguments": { ... } }
            { "tool": "RecordThought", "arguments": { ... } }
            { "tool": "SendMessage", "arguments": { ... } }
            ```
        """.trimIndent()

    suspend fun buildSystemPrompt(context: MessageContext): String {
        val personalityName = channelPersonality[context.primaryEvent.message.channelId.value] ?: "Jeeves"
        val personality = availableAgents[personalityName] ?: error("Personality not found: $personalityName")
        val shortPersonalityName = personality.name[0]

        //val toolList = toolModules.map {
        //    val r = "## ${it.toolName}\n${it.contextualDescription(context)}\n"
        //    println("Tool: ${it.toolName}, size: ${r.length}")
        //    r
        //}
        //val toolListStr =
        //    if (toolList.isNotEmpty()) "\n" + toolList.joinToString("\n")
        //    else "No tools available."

        // println("Personality: ${personality.name}, size: ${personality.personalityPrompt.length}")

        //# Tools
        //{{tools}}

        val sb = StringBuilder()
        for (tool in toolModules) {
            val sections = tool.sections(context)
            for (section in sections) {
                sb.append("# ${section.name}\n${section.content.trim()}\n\n")
            }
        }

        val systemPrompt =
            """
            {{TOOL_SECTIONS}}
            # Your Personality
            {{PERSONALITY}}

            # Communication Medium
            The user messages will have JSON format:
            ```
            {
                "user": "User Name",
                "message": "User message",
                "attachments": [{
                    "type": "image",
                    "url": "https://example.com/image.jpg"
                }, ...]
            }
            ```
            Messages are passed to and from the users through Discord, so you can use Discord syntax 
            (Markdown + Discord's extensions, e.g. ||<text>|| for hidden text) for formatting.
            You should avoid "pinging" users by mentioning them with <@ID> and especially avoid pinging @everyone or @here.
            
            {{PROMPT_END}}
            """.trimIndent()
                .replace("{{TOOL_SECTIONS}}", sb.toString())
                .replace("{{PERSONALITY}}", personality.personalityPrompt)
                .replace("{{PROMPT_END}}", if (context.isAnalysisMode) analysisModePrompt else nonAnalysisModePrompt)
                .trim()

        return systemPrompt
    }

    suspend fun respondToMessage(kord: Kord, discordEvent: MessageCreateEvent) {
        discordEvent.message.withReaction(ReactionEmoji.Unicode("\uD83E\uDD14")) {
            val messages = mutableListOf<ChatMessage>()

            // Add last channel messages
            var channelHistory = channelMessages[discordEvent.message.channelId.value] ?: emptyList()
            channelHistory = channelHistory.takeLast(10)
            for (lastMessage in channelHistory) {
                messages.add(lastMessage.toChatMessage())
            }

            // Remove any `tool` messages from the top of the list
            while (messages.isNotEmpty() && messages[0].role == ChatRole.Tool) {
                messages.removeAt(0)
            }

            val isAnalysis = run {
                val lastMessage = messages.lastOrNull()
                (lastMessage?.messageContent as? TextContent)?.content?.contains("ANALYSIS") == true
            }

            if (isAnalysis) {
                messages.add(messages.size - 1, ChatMessage(role = ChatRole.System, content = analysisModePrompt))
            }

            var iterCount = 0
            while (iterCount < 30) {
                iterCount += 1

                val personalityName = channelPersonality[discordEvent.message.channelId.value] ?: "Jeeves"
                val personality = availableAgents[personalityName] ?: error("Personality not found: $personalityName")
                val shortPersonalityName = personality.name[0]

                val context = MessageContext(discordEvent, personality)

                val systemPrompt = buildSystemPrompt(context)

                val toolList: MutableList<ChatTool> = this.toolModules.flatMap { it.functions }
                    .filter { it.predicate(context, config.superusers) }
                    .map { it.compiledToolSchema }
                    .toMutableList()

                val preparedMessages = mutableListOf<ChatMessage>()
                preparedMessages.add(ChatMessage(role = ChatRole.System, content = systemPrompt))
                preparedMessages.addAll(messages)

                val response = openAI.chatCompletion(
                    ChatCompletionRequest(
                        model = ModelId("gpt-4o"),
                        messages = preparedMessages,
                        maxTokens = 4096,
                        temperature = 1.0,
                        topP = 1.0,
                        tools = toolList,
                        toolChoice = ToolChoice.Mode("required")
                    )
                )

                val chatChoice = response.choices[0]
                messages.add(chatChoice.message)

                fun removeExtraPrefixes(responseMessage: ChatMessage): String? {
                    var content = responseMessage.content ?: return null
                    if (content.startsWith("Message from ${personalityName}:", ignoreCase = true)) {
                        content = content.removePrefix("Message from ${personalityName}:").trim()
                    }
                    if (content.startsWith("Message from ${shortPersonalityName}:", ignoreCase = true)) {
                        content = content.removePrefix("Message from ${shortPersonalityName}:").trim()
                    }
                    return content
                }

                if (chatChoice.message.content != null) {
                    val content = removeExtraPrefixes(chatChoice.message)
                    discordEvent.message.channel.createLongMessage(content!!, emptyList())
                }

                val toolMessages = mutableListOf<SMessage>()
                toolMessages.add(SMessage.ToolCall(chatChoice.message))

                val attachImages = mutableListOf<ResponseImage>()

                val toolCalls = chatChoice.message.toolCalls!!
                var isDone = true
                for (toolCall in toolCalls) {
                    toolCall as ToolCall.Function
                    val id = toolCall.id
                    val name = toolCall.function.name
                    val arguments = Json.decodeFromString<JsonObject>(toolCall.function.arguments)
                    val tool = toolModules.find { it.functions.any { it.compiledToolSchema.function.name == name } }

                    var result: ToolResponse? = null

                    if (tool == null) {
                        LOGGER.error("Tool not found: $name")
                        result = ToolResponse.InternalError("Tool not found: $name")
                    } else {
                        try {
                            LOGGER.info("Running tool: $name with arguments: ${toolCall.function.arguments}")
                            result = (tool as ToolModule<Any?>).execute(context, ToolModule.RawRequest(name, arguments))
                            LOGGER.info("Tool response: $result")
                        } catch (e: Throwable) {
                            if (e is VirtualMachineError) throw e
                            LOGGER.error("Error running tool: $name", e)
                            val fullMessage = "${e::class.simpleName}: ${e.message}"
                            result = ToolResponse.InternalError(fullMessage)
                        }
                    }
                    result ?: error("Tool response is null")

                    (result as? ToolResponse.Success)?.images?.forEach {
                        attachImages.add(it)
                    }

                    val resultMessage = ChatMessage(
                        toolCallId = id,
                        role = ChatRole.Tool,
                        name = name,
                        content = Json.encodeToString(result.toJson())
                    )

                    messages.add(resultMessage)
                    toolMessages.add(SMessage.ToolResponse(resultMessage))

                    if ((result as? ToolResponse.Success)?.forceContinue == true) {
                        isDone = false
                    }

                    if (result !is ToolResponse.Success) {
                        isDone = false
                    }

                    if (name != "DoNothing" && name != "SendMessage") {
                        isDone = false
                    }
                }

                channelMessages[discordEvent.message.channelId.value]?.addAll(toolMessages)

                for (image in attachImages) {
                    val content = ListContent(listOf(
                        ImagePart(url = image.base64),
                        TextPart(Json.encodeToString(JsonObject(mapOf("imageUrl" to JsonPrimitive(image.url)))))
                    ))
                    val message = ChatMessage(role = ChatRole.User, messageContent = content)
                    messages.add(message)
                }

                if (isDone) break
                continue
            }
        }
    }
}
