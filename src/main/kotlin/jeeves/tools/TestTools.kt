//package jeeves.tools
//
//import jeeves.Butler
//import jeeves.MessageContext
//import jeeves.society.Credits
//import jeeves.society.ToolModule
//import jeeves.society.ToolResponse
//import kotlinx.serialization.SerialName
//import kotlinx.serialization.Serializable
//
//class TestTools(val butler: Butler) : ToolModule<TestTools.Request>(Request.serializer()) {
//
//    @Serializable sealed interface Request {
//        @Serializable
//        @SerialName("TestSummarization")
//        data object TestSummarization : Request
//    }
//
//    override val toolName: String = "Test Tools"
//    override val description: String = "This tool is used for testing purposes."
//
//    override suspend fun contextualDescription(context: MessageContext): String = description
//
//    override suspend fun execute(context: MessageContext, req: Request): ToolResponse {
//        return when (req) {
//            is Request.TestSummarization -> {
//                val seenMessages = butler.channelMessages[context.primaryEvent.message.channelId.value]
//                if (!seenMessages.isNullOrEmpty()) {
//                    seenMessages.map { it.toChatMessage() }
//                }
//            }
//        }
//    }
//
//    override suspend fun estimateCost(context: MessageContext, req: Request?): Credits = Credits.MinToolCost
//}
