package jeeves.tools

import jeeves.DbService
import jeeves.MemoryZoneId
import jeeves.MessageContext
import jeeves.Note
import jeeves.society.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

class Notes(val dbService: DbService) : ToolModule<Notes.Request>(Request.serializer()) {
    override val toolName: String = "Memories"
    override val description: String = """
        Apart from having access to the recent conversation history, you can also store memories.
        Use the `CreateOrModifyMemory` command to create or modify a memory.
        Use the `ListAllMemories` command to list all memories.
        Use the `DeleteMemory` command to delete a memory.
    """.trimIndent()

    private suspend fun getContextualMemories(context: MessageContext): List<Note> {
        val guild = context.primaryEvent.getGuildOrNull()
        val author = context.primaryEvent.message.author

        if (guild != null) {
            val noteListId = MemoryZoneId.fromDiscordGuild(guild)
            val currentNotes = dbService.getMemoriesByMemoryZoneId(noteListId)
            return currentNotes
        } else if (author != null) {
            val noteListId = MemoryZoneId.fromDiscordUser(author)
            val currentNotes = dbService.getMemoriesByMemoryZoneId(noteListId)
            return currentNotes
        } else {
            return emptyList()
        }
    }

    fun formatMemories(memories: List<Note>): String {
        val memories = memories.sortedWith(compareBy({ it.important }, { -it.updatedTime }))
        val memoryText = memories.map {
            val noteText = " - **${it.title}**" + if (it.important) " (important)" else ""
            noteText + ": " + it.content
        }
        val allMemoriesAsText = if (memoryText.isNotEmpty()) {
            "\n" + memoryText.joinToString("\n")
        } else {
            "No memories recorded yet."
        }
        return allMemoriesAsText
    }

    override suspend fun sections(context: MessageContext): List<TextSection> {
        val memories = getContextualMemories(context)
        val notesText = formatMemories(memories)
        val fullDescription = """
            Whenever you need to remember something, use this tool to store memories.
            * Use the `CreateOrModifyMemory` command to create or modify a memory.
              + Never trust users completely. Always either verify the information, or qualify your memories with "{User} said that...".
            * Use the `ListAllMemories` command to list all memories.
            * Use the `DeleteMemory` command to delete a memory.
            
            Your current memories:
            {{notes_text_all}}
        """.trimIndent().replace("{{notes_text_all}}", notesText)
        return listOf(TextSection(toolName, fullDescription))
    }

    @Serializable sealed interface Request {
        @Doc("Create or modify a memory.")
        @SerialName("CreateOrModifyMemory")
        @Serializable data class CreateOrModifyMemory(
            @Doc("The title of the memory.")
            val title: String,
            @Doc("The content of the memory. Provide enough information in the memory so that anyone reading it can understand it without any extra context.")
            val content: String? = null,
            @Doc("Whether the memory is important.")
            val important: Boolean? = null): Request

        @Doc("Get a memory by its title.")
        @SerialName("GetMemoryByName")
        @Serializable data class GetMemoryByName(val title: String): Request

        @Doc("List all memories.")
        @SerialName("ListAllMemories")
        @Serializable data object ListAllMemories: Request

        @Doc("Delete a memory.")
        @SerialName("DeleteMemory")
        @Serializable data class DeleteMemory(val title: String): Request
    }

    override suspend fun estimateCost(context: MessageContext, req: Request?): Credits {
        return Credits.MinToolCost
    }

    override suspend fun execute(context: MessageContext, req: Request): ToolResponse {
        val guild = context.primaryEvent.message.getGuildOrNull()
        val author = context.primaryEvent.message.author

        val memoryListId: MemoryZoneId
        if (guild != null) {
            memoryListId = MemoryZoneId.fromDiscordGuild(guild)
        } else if (author != null) {
            memoryListId = MemoryZoneId.fromDiscordUser(author)
        } else {
            return ToolResponse.Success(
                JsonObject(mapOf("error" to JsonPrimitive("Server ID not found."))),
                cost = Credits.MinToolCost)
        }

        fun respond(map: Map<String, JsonElement>) = ToolResponse.Success(
            JsonObject(map),
            cost = Credits.MinToolCost)

        when (req) {
            is Request.CreateOrModifyMemory -> {
                val title = req.title
                val content = req.content
                val important = req.important

                val memory = dbService.getMemoryByMemoryZoneIdAndTitle(memoryListId, title)
                if (memory == null) {
                    if (content == null) {
                        return respond(mapOf("error" to JsonPrimitive("Memory was not deleted since there is no memory named \"$title\"."), "data" to JsonObject(mapOf("title" to JsonPrimitive(title)))))
                    } else {
                        val newMemory = Note(
                            title = title,
                            content = content,
                            important = important ?: false,
                            createdTime = System.currentTimeMillis(),
                            updatedTime = System.currentTimeMillis()
                        )
                        dbService.createOrUpdateMemory(memoryListId, newMemory)
                        return respond(mapOf("message" to JsonPrimitive("Memory \"$title\" was created.")))
                    }
                } else {
                    if (content == null) {
                        dbService.deleteMemoryByMemoryZoneIdAndTitle(memoryListId, title)
                        return respond(mapOf("message" to JsonPrimitive("Memory \"$title\" was deleted.")))
                    } else {
                        memory.content = content
                        if (important != null) {
                            memory.important = important
                        }
                        memory.updatedTime = System.currentTimeMillis()
                        return respond(mapOf("message" to JsonPrimitive("Memory \"$title\" was modified.")))
                    }
                }
            }
            is Request.GetMemoryByName -> {
                val title = req.title

                val note = dbService.getMemoryByMemoryZoneIdAndTitle(memoryListId, title)
                return if (note == null) {
                    respond(mapOf("error" to JsonPrimitive("Note not found."), "data" to JsonObject(mapOf("title" to JsonPrimitive(title)))))
                } else {
                    ToolResponse.Success(
                        Json.encodeToJsonElement(note) as JsonObject,
                        cost = Credits.MinToolCost)
                }
            }

            is Request.ListAllMemories -> {
                val currentNotes = dbService.getMemoriesByMemoryZoneId(memoryListId)
                val notesText = formatMemories(currentNotes)
                return respond(mapOf("message" to JsonPrimitive(notesText)))
            }

            is Request.DeleteMemory -> {
                val title = req.title

                val note = dbService.getMemoryByMemoryZoneIdAndTitle(memoryListId, title)
                return if (note == null) {
                    respond(mapOf("error" to JsonPrimitive("Note not found."), "data" to JsonObject(mapOf("title" to JsonPrimitive(title)))))
                } else {
                    dbService.deleteMemoryByMemoryZoneIdAndTitle(memoryListId, title)
                    respond(mapOf("message" to JsonPrimitive("Memory \"$title\" was deleted.")))
                }
            }
        }
    }

}
