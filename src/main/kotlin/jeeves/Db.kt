package jeeves

import dev.kord.common.entity.Snowflake
import dev.kord.core.behavior.MessageBehavior
import dev.kord.core.entity.Guild
import dev.kord.core.entity.Member
import dev.kord.core.entity.User
import dev.kord.core.entity.channel.GuildChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.datetime.Instant
import kotlinx.datetime.toJavaInstant
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Serializable @JvmInline
value class MemoryZoneId(val value: String) {
    companion object {
        fun fromDiscordGuild(guild: Guild): MemoryZoneId = MemoryZoneId("discord-guild-${guild.id.value}")
        fun fromDiscordUser(user: User): MemoryZoneId = MemoryZoneId("discord-user-${user.id.value}")
    }
}

@Serializable
data class DiscordUserInfo(
    val id: ULong,
    val username: String,
    val isBot: Boolean,

    val name: String,
    val avatarUrl: String?,

    val joinedCurrentGuild: String?,
    val communicationDisabledUntil: String?
)

@Serializable
data class DiscordChannelInfo(
    val id: ULong,
    val discordServerId: ULong,
    val name: String,
    val type: String
)

@Serializable
data class DiscordMessageInfo(
//    val id: ULong,
//    val authorId: ULong?,
//    val applicationId: ULong?,

//    val channelId: ULong,
//    val channelName: String?,
    val content: String,
    val timestamp: String,
//    val editedTimestamp: String?,
//    val referencedMessageId: ULong?,
//    val isBot: Boolean
)

@Serializable
data class DiscordServerInfo(
    val id: ULong,
    val name: String,
    val description: String?,
    val ownerId: ULong,
    val preferredLocale: String
)

class DbService(private val database: Database) {
    object JeevesUsers : Table() {
        val id = ulong("id").autoIncrement()

        val credits = long("credits")
        val dailyCredits = long("dailyCredits")
        val lastDailyCredits = long("lastDailyCredits")

        override val primaryKey = PrimaryKey(id)
    }

    object DiscordServers : Table() {
        // Discord group
        val id              = ulong("id")
        val name            = varchar("name", length = 64)
        val description     = text("description").nullable()
        val ownerId         = ulong("ownerId")
        val preferredLocale = varchar("preferredLocale", length = 64)
        override val primaryKey = PrimaryKey(id)
    }

    object DiscordChannels : Table() {
        // Discord channel
        val id      = ulong("id")
        val guildId = ulong("guildId")
        val name   = varchar("name", length = 64)
        val type = varchar("type", length = 64)
        override val primaryKey = PrimaryKey(id)
    }

    object DiscordUsers : Table() {
        val id       = ulong("id")
        val username = varchar("username", length = 64)
        val globalName = varchar("globalName", length = 64).nullable()
        val avatar = text("avatar").nullable()
        val isBot = bool("isBot")

        override val primaryKey = PrimaryKey(id)
    }

    object DiscordMemberships : Table() {
        val id       = ulong("id")
        val guildId  = ulong("guildId")

        val username = varchar("username", length = 64)
        val nickname = varchar("nickname", length = 64).nullable()
        val globalName = varchar("globalName", length = 64).nullable()
        val effectiveName = varchar("effectiveName", length = 64)
        val joinedAt = long("joinedAt")
        val communicationDisabledUntil = long("communicationDisabledUntil").nullable()
        val avatar = text("avatar").nullable()
        val memberAvatar = text("memberAvatar").nullable()
        val isBot = bool("isBot")

        override val primaryKey = PrimaryKey(id, guildId)
    }

    object DiscordMessages : Table() {
        val id = ulong("id")
        val channelId = ulong("channelId")
        val authorId = ulong("authorId").nullable()
        val applicationId = ulong("applicationId").nullable()
        val content = text("content")
        val timestamp = long("timestamp")
        val editedTimestamp = long("editedTimestamp").nullable()
        val referencedMessageId = ulong("referencedMessageId").nullable()
        // message.applicationId
        //            message.editedTimestamp
        //            message.messageReference?.message?.id?.value
        val isBot = bool("isBot")
        override val primaryKey = PrimaryKey(id)
    }

    object Memories : Table() {
        val noteZoneId = varchar("noteZoneId", length = 64)
        val title = text("title")
        val content = text("content")
        val important = bool("important")
        val createdTime = long("createdTime")
        val updatedTime = long("updatedTime")

        override val primaryKey = PrimaryKey(noteZoneId, title)
    }

    // Events can be scheduled either on absolute time or relative time to user's local time.
    // The event can be a one-time event or a recurring event.
    // The event can be a reminder, a notification, or a task.
    // The event can be a personal event or a group event.
    // The event can either have a fixed duration or a flexible duration.
    // The event may have no duration.
    // The event may have a location.


    init {
        transaction(database) {
            SchemaUtils.create(DiscordServers)
            SchemaUtils.create(DiscordChannels)
            SchemaUtils.create(DiscordUsers)
            SchemaUtils.create(DiscordMemberships)
            SchemaUtils.create(DiscordMessages)
            SchemaUtils.create(Memories)
        }
    }

    suspend fun <T> dbQuery(block: suspend () -> T): T =
        newSuspendedTransaction(Dispatchers.IO) { block() }

    suspend fun getMemoriesByMemoryZoneId(noteZoneId: MemoryZoneId): List<Note> = dbQuery {
        Memories.selectAll().where { Memories.noteZoneId eq noteZoneId.value }.map {
            Note(
                title = it[Memories.title],
                content = it[Memories.content],
                important = it[Memories.important],
                createdTime = it[Memories.createdTime],
                updatedTime = it[Memories.updatedTime]
            )
        }
    }

    suspend fun getMemoryByMemoryZoneIdAndTitle(zone: MemoryZoneId, title: String): Note? = dbQuery {
        Memories.selectAll().where { (Memories.noteZoneId eq zone.value) and (Memories.title eq title) }.map {
            Note(
                title = it[Memories.title],
                content = it[Memories.content],
                important = it[Memories.important],
                createdTime = it[Memories.createdTime],
                updatedTime = it[Memories.updatedTime]
            )
        }.singleOrNull()
    }

    suspend fun createOrUpdateMemory(zone: MemoryZoneId, note: Note): String = dbQuery {
        Memories.upsert {
            it[noteZoneId] = zone.value
            it[title] = note.title
            it[content] = note.content
            it[important] = note.important
            it[createdTime] = note.createdTime
            it[updatedTime] = note.updatedTime
        }[Memories.noteZoneId]
    }

    suspend fun deleteMemoryByMemoryZoneIdAndTitle(zone: MemoryZoneId, title: String): Int = dbQuery {
        Memories.deleteWhere { (Memories.noteZoneId eq zone.value) and (Memories.title eq title) }
    }

    suspend fun createDiscordGuildOrUpdate(guild: Guild): ULong = dbQuery {
        DiscordServers.upsert {
            it[id] = guild.id.value
            it[name] = guild.name
            it[description] = guild.description
            it[ownerId] = guild.ownerId.value
            it[preferredLocale] = guild.preferredLocale.toString()
        }[DiscordServers.id]
    }

    suspend fun createDiscordChannelOrUpdate(channel: GuildChannel): ULong = dbQuery {
        DiscordChannels.upsert {
            it[id] = channel.id.value
            it[name] = channel.name
            it[guildId] = channel.guildId.value
            it[type] = channel.type.toString()
        }[DiscordChannels.id]
    }

    suspend fun createOrUpdateDiscordUser(user: User): ULong = dbQuery {
        DiscordUsers.upsert {
            it[id] = user.id.value
            it[username] = user.username
            it[globalName] = user.globalName
            it[avatar] = user.avatar?.cdnUrl?.toUrl()
            it[isBot] = user.isBot
        }[DiscordUsers.id]
    }

    suspend fun hasDiscordUser(user: User): Boolean = dbQuery {
        DiscordUsers.selectAll().where { DiscordUsers.id eq user.id.value }.count() > 0
    }

    suspend fun hasDiscordMessage(messageId: ULong): Boolean = dbQuery {
        DiscordMessages.selectAll().where { DiscordMessages.id eq messageId }.count() > 0
    }
    suspend fun hasDiscordMessage(id: Snowflake): Boolean = hasDiscordMessage(id.value)
    suspend fun hasDiscordMessage(message: MessageBehavior): Boolean = hasDiscordMessage(message.id)

    suspend fun hasMember(guildId: Snowflake, userId: Snowflake): Boolean = dbQuery {
        DiscordMemberships.selectAll().where {
            (DiscordMemberships.id eq userId.value) and
            (DiscordMemberships.guildId eq guildId.value)
        }.count() > 0
    }

    suspend fun createOrUpdateDiscordMembership(user: Member): ULong = dbQuery {
        DiscordMemberships.upsert {
            it[id] = user.id.value
            it[guildId] = user.guildId.value
            it[username] = user.username
            it[nickname] = user.nickname
            it[globalName] = user.globalName
            it[effectiveName] = user.effectiveName
            it[joinedAt] = user.joinedAt.toEpochMilliseconds()
            it[communicationDisabledUntil] = user.communicationDisabledUntil?.toEpochMilliseconds()
            it[avatar] = user.avatar?.cdnUrl?.toUrl()
            it[memberAvatar] = user.memberAvatar?.cdnUrl?.toUrl()
            it[isBot] = user.isBot
        }[DiscordMemberships.id]
    }

    suspend fun createOrUpdateDiscordMessage(message: dev.kord.core.entity.Message): ULong = dbQuery {
        DiscordMessages.upsert {
            it[id] = message.id.value
            it[channelId] = message.channelId.value
            it[authorId] = message.author?.id?.value
            it[content] = message.content
            it[timestamp] = message.timestamp.toEpochMilliseconds()
            it[isBot] = message.author?.isBot ?: false
            it[referencedMessageId] = message.referencedMessage?.id?.value
            it[applicationId] = message.applicationId?.value
            it[editedTimestamp] = message.editedTimestamp?.toEpochMilliseconds()
        }[DiscordMessages.id]
    }

    suspend fun getDiscordServerInfo(guildId: ULong): DiscordServerInfo? = dbQuery {
        DiscordServers.selectAll().where { DiscordServers.id eq guildId }.map {
            DiscordServerInfo(
                id = it[DiscordServers.id],
                name = it[DiscordServers.name],
                description = it[DiscordServers.description],
                ownerId = it[DiscordServers.ownerId],
                preferredLocale = it[DiscordServers.preferredLocale]
            )
        }.singleOrNull()
    }

    suspend fun getDiscordChannelInfo(channelId: ULong): DiscordChannelInfo? = dbQuery {
        DiscordChannels.selectAll().where { DiscordChannels.id eq channelId }.map {
            DiscordChannelInfo(
                id = it[DiscordChannels.id],
                discordServerId = it[DiscordChannels.guildId],
                name = it[DiscordChannels.name],
                type = it[DiscordChannels.type]
            )
        }.singleOrNull()
    }

    val formatter = DateTimeFormatter
            .ofPattern("yyyy-MM-dd'T'hh:mm:ssxxx")
        .withZone(ZoneId.of("UTC"));

    suspend fun searchDiscordMessages(guildId: ULong?, channelId: ULong?, keywords: List<String>, limit: Int): List<DiscordMessageInfo> = dbQuery {
        val channelIds = mutableListOf<ULong>()
        if (channelId != null) {
            channelIds.add(channelId)
        } else if (guildId != null) {
            channelIds.addAll(DiscordChannels.selectAll().where { DiscordChannels.guildId eq guildId }.map { it[DiscordChannels.id] })
        } else {
            error { "guildId must be provided if channelId is not provided" }
        }

        val messages = DiscordMessages.selectAll().where {
            var cond: Op<Boolean>? = null
            if (channelIds.isNotEmpty()) {
                cond = DiscordMessages.channelId inList channelIds
            }
            if (keywords.isNotEmpty()) {
                var orCond: Op<Boolean> = Op.FALSE
                for (kw in keywords) {
                    orCond = orCond or (DiscordMessages.content like "%$kw%")
                }
                cond = if (cond == null) orCond else cond and orCond
            }
            cond!!
        }.map {
            DiscordMessageInfo(
//                id = it[Messages.id],
//                channelId = it[Messages.channelId],
//                authorId = it[Messages.authorId],
//                applicationId = it[Messages.applicationId],
                content = it[DiscordMessages.content],
                timestamp = formatter.format(Instant.fromEpochMilliseconds(it[DiscordMessages.timestamp]).toJavaInstant()),
//                editedTimestamp = it[Messages.editedTimestamp]?.let { ts -> formatter.format(Instant.fromEpochMilliseconds(ts).toJavaInstant()) },
//                referencedMessageId = it[Messages.referencedMessageId],
//                isBot = it[Messages.isBot]
            )
        }

        return@dbQuery messages.sortedByDescending { it.timestamp }.take(limit)
    }

    suspend fun getDiscordUserInfo(guildId: ULong?, userId: ULong?, userName: String?): DiscordUserInfo? = dbQuery {
        require(userId != null || userName != null) { "userId or userName must be provided" }

        val member = DiscordMemberships.selectAll().where {
            var cond: Op<Boolean>? = null
            if (guildId != null) {
                cond = DiscordMemberships.guildId eq guildId
            }
            if (userId != null) {
                cond = if (cond == null) DiscordMemberships.id eq userId else cond and (DiscordMemberships.id eq userId)
            }
            if (userName != null) {
                cond = if (cond == null) ((DiscordMemberships.username eq userName) or (DiscordMemberships.effectiveName eq userName)) else cond and ((DiscordMemberships.username eq userName) or (DiscordMemberships.effectiveName eq userName))
            }

            cond!!
        }.map {
            DiscordUserInfo(
                id = it[DiscordMemberships.id],
                username = it[DiscordMemberships.username],
                isBot = it[DiscordMemberships.isBot],
                name = it[DiscordMemberships.effectiveName],
                avatarUrl = it[DiscordMemberships.memberAvatar] ?: it[DiscordMemberships.avatar],
                joinedCurrentGuild = formatter.format(Instant.fromEpochMilliseconds(it[DiscordMemberships.joinedAt]).toJavaInstant()),
                communicationDisabledUntil = formatter.format(Instant.fromEpochMilliseconds(it[DiscordMemberships.communicationDisabledUntil] ?: 0).toJavaInstant())
            )
        }.singleOrNull()

        if (member != null) return@dbQuery member

        val user = DiscordUsers.selectAll().where {
            var cond: Op<Boolean>? = null
            if (userId != null) {
                cond = DiscordUsers.id eq userId
            }
            if (userName != null) {
                cond = if (cond == null) (DiscordUsers.username eq userName)
                else cond!! and (DiscordUsers.username eq userName)
            }

            cond!!
        }.map {
            DiscordUserInfo(
                id = it[DiscordUsers.id],
                username = it[DiscordUsers.username],
                isBot = it[DiscordUsers.isBot],
                name = it[DiscordUsers.globalName] ?: it[DiscordUsers.username],
                avatarUrl = it[DiscordUsers.avatar],
                joinedCurrentGuild = null,
                communicationDisabledUntil = null
            )
        }.singleOrNull()

        return@dbQuery user
    }
}
