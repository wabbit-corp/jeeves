package jeeves.tools

import dev.kord.common.entity.Snowflake
import dev.kord.core.Kord
import dev.kord.core.entity.channel.DmChannel
import dev.kord.core.entity.channel.TextChannel
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.util.*
import jeeves.DbService
import jeeves.EasyJson
import jeeves.MessageContext
import jeeves.society.Credits
import jeeves.society.ResponseImage
import jeeves.society.ToolModule
import jeeves.society.ToolResponse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import jeeves.society.Doc

class Discord(val httpClient: HttpClient, val dbService: DbService, val kord: Kord) : ToolModule<Discord.Request>(Request.serializer()) {
    override val toolName: String = "Discord Servers, Channels, Users, and Messages"
    override val description: String = """
            This tool provides access to Discord channels, users, and messages.
            Use `get_discord_channel_info` to get information about a channel and the current discord server.
            Use `get_discord_user_info` to get information about a user.
            Use `search_discord_messages` to search for messages in a guild or a channel. 
            You should come up with a comprehensive list of keywords to search for based on the user's intent.
        """.trimIndent()
    override suspend fun contextualDescription(context: MessageContext): String = description

    @Serializable sealed interface Request {
        @Doc("Get information about a user.")
        @SerialName("GetDiscordUserInfo")
        @Serializable data class GetDiscordUserInfo(
            @Doc("The ID of the user: a string, a number, or null if the user name is provided.")
            val userId: String? = null,
            @Doc("The name of the user.")
            val userName: String? = null,
            @Doc("Include the user's avatar?")
            val includeUserAvatar: Boolean): Request

        @Doc("Search for messages in a guild or a channel.")
        @SerialName("SearchDiscordMessages")
        @Serializable data class SearchDiscordMessages(
            @Doc("The keywords to search for. At least one keyword must be provided and any message that contains any of the keywords will be returned.")
            val keywords: List<String>,
            @Doc("The ID of the channel or null if all channels in the guild should be searched.")
            val channelId: ULong? = null,
            @Doc("The maximum number of messages to return. The maximum is 30.")
            val limit: Int? = null): Request

        @Doc("Get information about the current channel.")
        @SerialName("GetDiscordChannelInfo")
        @Serializable data class GetDiscordChannelInfo(
            @Doc("The ID of the channel. If null, the current channel will be used.")
            val channelId: ULong? = null): Request
    }

    override suspend fun estimateCost(context: MessageContext, req: Request?): Credits {
        return Credits.MinToolCost
    }

    override suspend fun execute(context: MessageContext, req: Request): ToolResponse {
        when (req) {
            is Request.GetDiscordUserInfo -> {
                val userId = req.userId
                val userName = req.userName
                val includeUserAvatar = req.includeUserAvatar

                if (userId == null && userName == null) {
                    return ToolResponse.Success(
                        EasyJson.of(
                            "error" to "User ID or user name must be provided."
                        ),
                        cost = Credits.MinToolCost
                    )
                }

                var resolvedUserId: ULong?
                var resolvedUserName: String?

                if (userId != null) {
                    // If the user ID is not a number, it may be a user name.
                    resolvedUserId = userId.toULongOrNull()
                    if (resolvedUserId == null) {
                        resolvedUserName = userId
                    } else {
                        resolvedUserName = userName
                    }
                } else {
                    resolvedUserId = null
                    resolvedUserName = userName
                }

                var user = dbService.getDiscordUserInfo(context.primaryEvent.guildId?.value, resolvedUserId, resolvedUserName)
                if (user == null) {
                    if (resolvedUserId != null) {
                        val member = context.primaryEvent.getGuildOrNull()?.getMember(Snowflake(resolvedUserId))
                        if (member != null) {
                            dbService.createOrUpdateDiscordMembership(member)
                            dbService.createOrUpdateDiscordUser(member)
                        } else {
                            val user = kord.getUser(Snowflake(resolvedUserId))
                            if (user != null) {
                                dbService.createOrUpdateDiscordUser(user)
                            }
                        }

                        user = dbService.getDiscordUserInfo(context.primaryEvent.guildId?.value, resolvedUserId, resolvedUserName)
                    }

                    if (user == null)
                        return ToolResponse.Success(
                            EasyJson.of("error" to "User not found."),
                            cost = Credits.MinToolCost
                        )
                }

                val userJson = JsonObject(
                    mapOf(
                        "id" to JsonPrimitive(user.id.toString()),
                        "username" to JsonPrimitive(user.username),
                        "isBot" to JsonPrimitive(user.isBot),
                        "name" to JsonPrimitive(user.name),
                        "joinedCurrentGuild" to JsonPrimitive(user.joinedCurrentGuild),
                        "communicationDisabledUntil" to JsonPrimitive(user.communicationDisabledUntil)
                    )
                )

                val image = if (includeUserAvatar) {
                    val avatarUrl = user.avatarUrl
                    if (avatarUrl != null) {
                        val response = httpClient.get(avatarUrl)
                        val content = response.readBytes()
                        val contentType = response.headers["Content-Type"]
                        val base64 = content.encodeBase64()
                        ResponseImage(avatarUrl, "data:$contentType;base64,$base64")
                    } else {
                        null
                    }
                } else {
                    null
                }

                return ToolResponse.Success(
                    userJson,
                    images = listOfNotNull(image),
                    cost = Credits.MinToolCost)
            }

            is Request.SearchDiscordMessages -> {
                val keywords = req.keywords
                val channelId = req.channelId
                var limit = req.limit

                limit = if (limit != null && limit > 30) 30 else limit
                limit = if (limit != null && limit <= 0) 0 else limit
                val messages = dbService.searchDiscordMessages(context.primaryEvent.guildId?.value, channelId, keywords, limit ?: 30)

                val messagesJson = Json.encodeToJsonElement(messages)

                return ToolResponse.Success(
                    messagesJson,
                    cost = Credits.MinToolCost)
            }

            is Request.GetDiscordChannelInfo -> {
                val channel = context.primaryEvent.message.channel.asChannel()

                val fields = mutableMapOf<String, JsonElement>()

                fields["id"] = JsonPrimitive(channel.id.value.toLong())

                if (channel is TextChannel) {
                    fields["name"] = JsonPrimitive(channel.name)
                    fields["type"] = JsonPrimitive(channel.type.toString())
                    fields["serverId"] = JsonPrimitive(channel.guildId.value.toLong())

                    val guild = channel.guild.asGuild()

                    fields["serverInfo"] = JsonObject(
                        mapOf(
                            "id" to JsonPrimitive(guild.id.value.toLong()),
                            "name" to JsonPrimitive(guild.name),
                            "ownerId" to JsonPrimitive(guild.ownerId.value.toLong()),
                            "memberCount" to JsonPrimitive(guild.memberCount),
                            "description" to JsonPrimitive(guild.description)
                        )
                    )

                } else if (channel is DmChannel) {
                    fields["type"] = JsonPrimitive("DM")
                    fields["recipientIds"] = JsonArray(channel.recipientIds.map { JsonPrimitive(it.value.toLong()) })
                }

                return ToolResponse.Success(
                    JsonObject(fields),
                    cost = Credits.MinToolCost)
            }
        }
    }

}
