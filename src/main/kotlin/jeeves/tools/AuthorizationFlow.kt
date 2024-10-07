package jeeves.tools

//import com.aallam.openai.api.chat.FunctionTool
//import com.aallam.openai.api.chat.ToolType
//import jeeves.MessageContext
//import kotlinx.serialization.json.JsonObject
//import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeRequestUrl
//
//class AuthorizationFlow : Tool {
//    override fun name(context: MessageContext): String = "Authorization Flow"
//
//    override fun description(context: MessageContext): String {
//        if (context.primaryEvent.message.author?.id == null)
//            return """
//                Unavailable
//            """.trimIndent()
//
//        val author = context.primaryEvent.message.author!!
//
//        return """
//            This will initiate the authorization flow for the user ${author.username}#${author.discriminator} to access their Google Calendar.
//        """.trimIndent()
//    }
//
//    override val functions: List<ChatTool> = listOf(
//        ChatTool(
//            type = ToolType.Function,
//            function = FunctionTool(
//                name = "authorize",
//                description = "Call it if the user has requested the assistant to access their calendar."
//            )
//        )
//    )
//
//    override suspend fun run(context: MessageContext, name: String, args: JsonObject): Tool.Response {
//        require(name == "authorize") { "Invalid function name" }
//
//        val author = context.primaryEvent.message.author ?: return Tool.Response("Unavailable")
//        val dm = author.getDmChannel()
//
//        val url = GoogleAuthorizationCodeRequestUrl(
//            "CLIENT_ID",
//            "https://redirect-uri.com",
//            listOf("https://www.googleapis.com/auth/calendar")
//        ).build()
//
//        dm.createMessage("Please click on the following link to authorize the assistant to access your Google Calendar: https://google.com")
//    }
//}
