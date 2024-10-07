package jeeves

import com.aallam.openai.api.http.Timeout
import com.aallam.openai.api.logging.LogLevel
import com.aallam.openai.client.LoggingConfig
import com.aallam.openai.client.OpenAI
import com.aallam.openai.client.OpenAIConfig
import dev.kord.common.entity.ChannelType
import dev.kord.common.entity.Snowflake
import dev.kord.core.Kord
import dev.kord.core.entity.Member
import dev.kord.core.entity.Message
import dev.kord.core.event.message.MessageCreateEvent
import dev.kord.core.on
import dev.kord.gateway.Intent
import dev.kord.gateway.PrivilegedIntent
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.util.*
import jeeves.tools.*
import kotlinx.datetime.toJavaInstant
import kotlinx.serialization.Serializable
import lang.mu.MuParser
import lang.mu.evaluateMu
import lang.mu.std.MuStdContext
import org.jetbrains.exposed.sql.Database
import org.slf4j.LoggerFactory
import kotlin.time.Duration.Companion.seconds

data class AgentDescription(
    val name: String,
    val imagePath: String?,
    val respondsTo: List<String>,
    val shortDescription: String,
    val personalityPrompt: String,
    val physicalAppearance: String?
) {
    val respondsToRegex: Regex = ("\\b(?:" + (respondsTo + listOf("J")).joinToString("|") { Regex.escape(it) } + ")\\b")
        .toRegex(RegexOption.IGNORE_CASE)
}

@Serializable data class Note(
    val title: String,
    var content: String,
    var important: Boolean,
    var updatedTime: Long,
    val createdTime: Long
)

data class MessageContext(
    val primaryEvent: MessageCreateEvent,
    val personality: AgentDescription
) {
    val userId: String = "discord:${primaryEvent.message.author?.id?.value?.toString()}"
    val isAnalysisMode = primaryEvent.message.content.contains("ANALYSIS")
}

//interface PythonPackage {
//    @Python.Func("""
//        def embed(sentence):
//            from sentence_transformers import SentenceTransformer, util
//            if not hasattr(G, 'model'):
//                G.model = SentenceTransformer('all-MiniLM-L6-v2')
//            embedding = G.model.encode([sentence])[0]
//            return [float(x) for x in embedding]
//    """)
//    suspend fun embed(value: String): List<Double>
//
//    @Python.Func("""
//        def execute(code):
//            import io
//            import builtins
//            buf = io.StringIO()
//            def print(*args, **kwargs):
//                builtins.print(*args, **kwargs, file=buf)
//            exec(code)
//            return buf.getvalue()
//    """)
//    suspend fun execute(value: String): String
//}

suspend fun main() {
    val logger = LoggerFactory.getLogger("Jeeves")
    val jeevesConfigScript = MuParser.parseFile("./data-jeeves/jeeves.clj").map { it.lower() }

    val config = ConfigBuilder()
    val context = MuStdContext.empty()
        .withNativeModule("config-builder", config)
        .withOpenModule("config-builder")
    for (expr in jeevesConfigScript) {
        evaluateMu(context, expr, MuStdContext.Companion)
    }

    val kord = Kord(config.discordToken!!)
    val httpClient = HttpClient(CIO) {
        // timeout and retry policy
        install(HttpTimeout) {
            requestTimeoutMillis = 60000 * 3
            connectTimeoutMillis = 60000
            socketTimeoutMillis = 60000
        }

        // user agent
        install(UserAgent) {
            agent = config.userAgent!!
        }
    }

    val openai = OpenAI(
        OpenAIConfig(
            token = config.openaiKey!!,
            timeout = Timeout(socket = 120.seconds),
            logging = LoggingConfig(
                logLevel = LogLevel.None,
                logger = com.aallam.openai.api.logging.Logger.Simple
            )
        )
    )

    val database = Database.connect(
        url = "jdbc:sqlite:data.db",
        driver = "org.sqlite.JDBC",
    )

    val dbService = DbService(database)

    val downloader = Downloader(httpClient, database, java.io.File("./data-jeeves/tmp/"))

    // Python.withFFIAsync<PythonPackage> { pythonPackage ->
    val butler = Butler(kord, openai, httpClient, config, database)
    butler.addTool(Weather(httpClient))
    val imgflip = Imgflip.create(httpClient, config.imgflipUsername!!, config.imgflipPassword!!, downloader)
    butler.addTool(imgflip)
    val nsfwChecker = NSFWChecker.loadFromFile("./data-jeeves/bad_words.txt")
    butler.addTool(Jokes.loadFromFile("./data-jeeves/joke-dataset/reddit_jokes.json", nsfwChecker))
    butler.availableAgents.putAll(config.personalities)
    butler.addTool(ImageGeneration(openai, downloader))
    butler.addTool(CurrentDateTime())
    butler.addTool(WebSearch(httpClient, config.braveKey!!))
    butler.addTool(Tasks(dbService))
    butler.addTool(ComplexProblem(openai))
    // butler.addTool(CodeInterpreter(pythonPackage))
    butler.addTool(Notes(dbService))
    butler.addTool(Discord(httpClient, dbService, kord))
    butler.addTool(Messaging(downloader))
    butler.addTool(ReadingAttachments(downloader))
    butler.addTool(SwitchPersonality(butler))

    //    assistant.addTool(URLSummarizer(httpClient, config.kagiToken!!))

    kord.on<MessageCreateEvent> {
        // Save the author to the database.
        val author = message.author
        if (author != null) dbService.createOrUpdateDiscordUser(author)

        // Save the guild and membership to the database.
        val guild = message.getGuildOrNull()
        val member: Member?
        if (author != null && guild != null) {
            member = author.fetchMember(guild.id)
            dbService.createOrUpdateDiscordMembership(member)
        } else {
            member = null
        }

        if (author?.isBot != false) return@on

        dbService.createOrUpdateDiscordMessage(message)

        if (author?.id == kord.selfId) return@on

        val content = message.content

        val attachments = message.attachments.map {
            SMessage.Attachment(
                it.id.value.toString(),
                it.filename,
                it.url,
                it.contentType)
        }

        val images = message.attachments.filter { it.isImage }.map {
            // Download the attachment
            val response = httpClient.get(it.url)
            val content = response.readBytes()
            val base64 = content.encodeBase64()
            "data:${it.contentType};base64,$base64"
        }

        logger.info("Received message: ${author?.username}: $content")

        val userMessage = SMessage.User(
            userId = author?.id?.value?.toLong()?.let { UserId(it.toString()) },
            userName = member?.effectiveName ?: author?.username,
            messageId = message.id.value.toLong().let { MessageId(it.toString()) },
            sentTime = message.timestamp.toJavaInstant(),
            lastEditTime = message.editedTimestamp?.toJavaInstant(),
            message = content,
            images = images,
            attachments = attachments)

        butler.channelMessages.computeIfAbsent(message.channelId.value) { mutableListOf() }.add(userMessage)
        butler.channelSeenAttachments.computeIfAbsent(message.channelId.value) { mutableListOf() }.addAll(attachments)

        val agentName = butler.channelPersonality[message.channelId.value] ?: "Jeeves"
        val agentDescription = butler.availableAgents[agentName] ?: error("Personality not found: $agentName")

        val foundName = content.contains(agentDescription.respondsToRegex)
        val isPrivateChannel = message.channel.fetchChannel().type == ChannelType.DM
        val isMentioned = message.mentionedUserIds.any { it == kord.selfId }

        if (isPrivateChannel || foundName || isMentioned) {
            butler.respondToMessage(kord, this)
        }
    }

    suspend fun indexMessage(guildId: Snowflake, message: Message) {
        val author = message.author
        if (author != null) {
            dbService.createOrUpdateDiscordUser(author)
            if (!dbService.hasMember(guildId, author.id)) try {
                val member = author.asMember(guildId)
                dbService.createOrUpdateDiscordMembership(member)
            } catch (e: Throwable) {
                if (e is VirtualMachineError) throw e
            }
        }
        if (message.author?.isBot == false || message.author?.id == kord.selfId)
            dbService.createOrUpdateDiscordMessage(message)
    }

//    kord.on<ReadyEvent> {
//        println("Ready!")
//        coroutineScope {
//            launch {
//                kord.guilds.collect { guild ->
//                    dbService.createGuildOrUpdate(guild)
//                    guild.channels.collect { channel ->
//                        dbService.createChannelOrUpdate(channel)
//                        when (channel.type) {
//                            ChannelType.GuildText -> {
//                                val guildChannel = channel as TextChannel
//                                val lastMessage = guildChannel.getLastMessage()
//                                if (lastMessage != null) {
//                                    indexMessage(channel.guildId, lastMessage)
//
////                                    launch {
////                                        channel.getMessagesBefore(lastMessage.id, 100).collect { message ->
////                                            indexMessage(channel.guildId, message)
////                                        }
////                                    }
//                                }
//                            }
//                            else -> { }
//                        }
//                    }
//                }
//            }
//        }
//    }

    kord.login {
        // we need to specify this to receive the content of messages
        @OptIn(PrivilegedIntent::class)
        intents += Intent.MessageContent

        @OptIn(PrivilegedIntent::class)
        intents += Intent.GuildMembers

        intents += Intent.Guilds
        intents += Intent.GuildMessages
        intents += Intent.GuildMessageReactions
    }
}
