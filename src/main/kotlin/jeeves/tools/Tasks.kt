package jeeves.tools

import jeeves.*
import jeeves.society.Credits
import jeeves.society.ToolModule
import jeeves.society.ToolResponse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import jeeves.society.Doc
import java.io.StringWriter

class Tasks(val dbService: DbService) : ToolModule<Tasks.Request>(Request.serializer()) {
    override val toolName: String = "Tasks"
    override val description: String = """
        You can use this tool to create tasks, goals, objectives, etc.
    """.trimIndent()

    override suspend fun contextualDescription(context: MessageContext): String = description

    @Serializable enum class FeaturePriority {
        Low, Medium, High, Critical
    }
    @Serializable enum class FeatureUrgency {
        Low, Medium, High, Immediate
    }
    @Serializable enum class ImpactScore {
        Minimal, Moderate, Significant, Transformative
    }
    @Serializable enum class BugSeverity {
        Low, Medium, High, Critical
    }

    @Serializable sealed interface Request {
        @Doc("Request a new feature for the assistant: a new tool, a new capability, or any other improvement.")
        @SerialName("RequestFeature")
        @Serializable data class RequestFeature(
            @Doc("Describe the feature you would like to request in the greatest detail you can.")
            val feature: String,
            @Doc("List of potential use case scenarios for this feature.")
            val useCaseScenarios: List<String>,
            @Doc("Anticipated improvements in performance, efficiency, or user experience.")
            val expectedImpact: String,
            @Doc("Specific feedback from users or stakeholders that led to this feature request.")
            val userFeedback: String,
            @Doc("How this feature would interact with existing functionalities.")
            val integrationPoints: List<String>? = null,
            @Doc("Estimated impact of the feature on overall system capabilities.")
            val estimatedImpactScore: ImpactScore,
            @Doc("The priority level of this feature request.")
            val priority: FeaturePriority,
            @Doc("The urgency level of this feature request.")
            val urgency: FeatureUrgency,
            @Doc("Specific metrics that will be used to measure the success of this feature.")
            val successMetrics: List<String>
        ) : Request

        @Doc("Report a bug or issue in the assistant's functionality. E.g. if a tool errors out, file a report immediately.")
        @SerialName("RequestBug")
        @Serializable data class RequestBug(
            @Doc("Describe the bug in detail, including what happened and what was expected.")
            val bugDescription: String,
            @Doc("List of steps to reproduce the bug.")
            val stepsToReproduce: List<String>,
            @Doc("Specific functionality or feature affected by the bug.")
            val affectedFunctionality: String,
            @Doc("Severity of the bug.")
            val severity: BugSeverity,
            @Doc("Description of the bug's impact on user experience or system functionality.")
            val impact: String,
            @Doc("List of tools or features that may be related to the bug.")
            val relatedTools: List<String>? = null,
            @Doc("Proposed solution to the bug.")
            val proposedSolution: String? = null
        ) : Request
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
            is Request.RequestFeature -> {
                val sb = StringWriter()
                fun println(s: String) { sb.append(s).append('\n') }
                println("> **Feature Request:**")
                println("> Feature: ${req.feature}")
                println("> Use Case Scenarios: ${req.useCaseScenarios}")
                println("> Expected Impact: ${req.expectedImpact}")
                println("> User Feedback: ${req.userFeedback}")
                println("> Integration Points: ${req.integrationPoints}")
                println("> Estimated Impact Score: ${req.estimatedImpactScore}")
                println("> Priority: ${req.priority}")
                println("> Urgency: ${req.urgency}")
                println("> Metrics for Success: ${req.successMetrics}")
                context.primaryEvent.message.channel.createLongMessage(sb.toString(), emptyList())
                return ToolResponse.Success(JsonObject(mapOf()), cost = Credits.MinToolCost)
            }

            is Request.RequestBug -> {
                val sb = StringWriter()
                fun println(s: String) { sb.append(s).append('\n') }
                println("> **Bug Report:**")
                println("> Bug Description: ${req.bugDescription}")
                println("> Steps to Reproduce: ${req.stepsToReproduce}")
                println("> Affected Functionality: ${req.affectedFunctionality}")
                println("> Severity: ${req.severity}")
                println("> Impact: ${req.impact}")
                println("> Related Tools: ${req.relatedTools}")
                println("> Proposed Solution: ${req.proposedSolution}")
                context.primaryEvent.message.channel.createLongMessage(sb.toString(), emptyList())
                return ToolResponse.Success(JsonObject(mapOf()), cost = Credits.MinToolCost)
            }

//            is Request.CreateOrModifyMemory -> {
//                val title = req.title
//                val content = req.content
//                val important = req.important
//
//                val memory = dbService.getMemoryByMemoryZoneIdAndTitle(memoryListId, title)
//                if (memory == null) {
//                    if (content == null) {
//                        return respond(mapOf("error" to JsonPrimitive("Memory was not deleted since there is no memory named \"$title\"."), "data" to JsonObject(mapOf("title" to JsonPrimitive(title)))))
//                    } else {
//                        val newMemory = Note(
//                            title = title,
//                            content = content,
//                            important = important ?: false,
//                            createdTime = System.currentTimeMillis(),
//                            updatedTime = System.currentTimeMillis()
//                        )
//                        dbService.createOrUpdateMemory(memoryListId, newMemory)
//                        return respond(mapOf("message" to JsonPrimitive("Memory \"$title\" was created.")))
//                    }
//                } else {
//                    if (content == null) {
//                        dbService.deleteMemoryByMemoryZoneIdAndTitle(memoryListId, title)
//                        return respond(mapOf("message" to JsonPrimitive("Memory \"$title\" was deleted.")))
//                    } else {
//                        memory.content = content
//                        if (important != null) {
//                            memory.important = important
//                        }
//                        memory.updatedTime = System.currentTimeMillis()
//                        return respond(mapOf("message" to JsonPrimitive("Memory \"$title\" was modified.")))
//                    }
//                }
//            }
//            is Request.GetMemoryByName -> {
//                val title = req.title
//
//                val note = dbService.getMemoryByMemoryZoneIdAndTitle(memoryListId, title)
//                return if (note == null) {
//                    respond(mapOf("error" to JsonPrimitive("Note not found."), "data" to JsonObject(mapOf("title" to JsonPrimitive(title)))))
//                } else {
//                    Tool.Response(
//                        Json.encodeToJsonElement(note) as JsonObject,
//                        cost = Credits.MinToolCost)
//                }
//            }
//
//            is Request.ListAllMemories -> {
//                val currentNotes = dbService.getMemoriesByMemoryZoneId(memoryListId)
//                val notesText = formatMemories(currentNotes)
//                return respond(mapOf("message" to JsonPrimitive(notesText)))
//            }
//
//            is Request.DeleteMemory -> {
//                val title = req.title
//
//                val note = dbService.getMemoryByMemoryZoneIdAndTitle(memoryListId, title)
//                return if (note == null) {
//                    respond(mapOf("error" to JsonPrimitive("Note not found."), "data" to JsonObject(mapOf("title" to JsonPrimitive(title)))))
//                } else {
//                    dbService.deleteMemoryByMemoryZoneIdAndTitle(memoryListId, title)
//                    respond(mapOf("message" to JsonPrimitive("Memory \"$title\" was deleted.")))
//                }
//            }
        }
    }

}
