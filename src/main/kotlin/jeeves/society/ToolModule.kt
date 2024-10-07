package jeeves.society

import jeeves.MessageContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

@Serializable data class ResponseImage(
    val url: String,
    val base64: String
)

@Serializable sealed interface ToolResponse {
    fun toJson(): JsonElement

    @Serializable data class InvalidInput(val message: String) : ToolResponse {
        override fun toJson(): JsonObject = JsonObject(mapOf("error" to JsonPrimitive(message)))
    }
    @Serializable data class InternalError(val message: String) : ToolResponse {
        override fun toJson(): JsonObject = JsonObject(mapOf("error" to JsonPrimitive(message)))
    }
    @Serializable data class Success(
        val data: JsonElement,
        val cost: Credits,
        val images: List<ResponseImage> = emptyList(),
        val forceContinue: Boolean = false
    ) : ToolResponse {
        override fun toJson(): JsonElement = data
    }
}

@Serializable data class TextSection(val name: String, val content: String)

abstract class ToolModule<Request>(val serializer: KSerializer<Request>) {
    abstract val toolName: String
    abstract val description: String

    open suspend fun contextualDescription(context: MessageContext): String = description
    open suspend fun sections(context: MessageContext): List<TextSection> = emptyList()

    abstract suspend fun estimateCost(context: MessageContext, req: Request?): Credits
    abstract suspend fun execute(context: MessageContext, req: Request): ToolResponse

    @Serializable data class RawRequest(
        val name: String,
        val args: JsonObject
    )

    // private val typeDef = FunctionSchema.def<>()

    val functions: List<ToolDef> = FunctionSchema.makeFunctions(serializer.descriptor)

    fun parseRequest(context: MessageContext, req: RawRequest): Request = FunctionSchema.parseRequest(serializer, req)

    suspend fun estimateCost(context: MessageContext, req: RawRequest?): Credits =
        estimateCost(context, req?.let { parseRequest(context, it) })

    suspend fun execute(context: MessageContext, req: RawRequest): ToolResponse {
        val parsed = try {
            parseRequest(context, req)
        } catch (e: Exception) {
            return ToolResponse.InvalidInput("${e.javaClass.simpleName} :: ${e.message ?: "Invalid input."}")
        }

        val result = try {
            execute(context, parsed)
        } catch (e: Exception) {
            return ToolResponse.InternalError("${e.javaClass.simpleName} :: ${e.message ?: "An internal error occurred."}")
        }

        return result
    }

    @Serializable
    sealed interface EmptyRequest

    abstract class NoFunctions : ToolModule<EmptyRequest>(EmptyRequest.serializer()) {
        override suspend fun estimateCost(context: MessageContext, req: EmptyRequest?): Credits {
            error("This tool does not have any functions.")
        }
        override suspend fun execute(context: MessageContext, req: EmptyRequest): ToolResponse {
            error("This tool does not have any functions.")
        }
    }
}
