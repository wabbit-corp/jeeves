//package jeeves.tools
//
//import jeeves.MessageContext
////import jeeves.PythonPackage
//import jeeves.society.*
//import kotlinx.serialization.SerialName
//import kotlinx.serialization.Serializable
//import kotlinx.serialization.json.JsonPrimitive
//
//class CodeInterpreter(val python: PythonPackage) : ToolModule<CodeInterpreter.Request>(Request.serializer()) {
//    @Serializable
//    sealed interface Request {
//        @Requires("superUser && inDM")
//        @Doc("Runs Python code. You can use print statements to output data.")
//        @SerialName("RunPython")
//        @Serializable data class RunPython(val code: String) : Request
//    }
//
//    override val toolName: String = "Code Interpreter"
//    override val description: String = """
//        This tool is designed to help you run Python code.
//        Use `run_python` to run Python code.
//    """.trimIndent()
//
//    override suspend fun contextualDescription(context: MessageContext): String = description
//    override suspend fun execute(context: MessageContext, req: Request): ToolResponse {
//        when (req) {
//            is Request.RunPython -> {
//                context.primaryEvent.message.channel.createMessage(
//                    "> Running\n" +
//                            "```python\n" +
//                            req.code +
//                            "\n```"
//                )
//                val r = try {
//                    python.execute(req.code)
//                } catch (e: Exception) {
//                    context.primaryEvent.message.channel.createMessage(
//                        "> Error\n" +
//                                "```python\n" +
//                                e.message +
//                                "\n```"
//                    )
//                    throw e
//                }
//                return ToolResponse.Success(data = JsonPrimitive(r), cost = Credits.MinToolCost)
//            }
//        }
//    }
//
//    override suspend fun estimateCost(context: MessageContext, req: Request?): Credits =
//        Credits.MinToolCost
//}
