package jeeves

import dev.kord.common.entity.Snowflake
import dev.kord.core.behavior.channel.MessageChannelBehavior
import dev.kord.core.behavior.channel.createMessage
import dev.kord.core.behavior.channel.withTyping
import dev.kord.core.entity.ReactionEmoji
import io.ktor.client.request.forms.*
import io.ktor.util.cio.*

suspend fun dev.kord.core.entity.Message.withReaction(reaction: ReactionEmoji, block: suspend () -> Unit) {
    var added = false
    try {
        this.addReaction(reaction)
        added = true
    } catch (e: Throwable) {
        if (e is VirtualMachineError) throw e
    }

    try {
        this.channel.withTyping {
            block()
        }
    } finally {
        if (added) {
            try {
                this.deleteOwnReaction(reaction)
            } catch (e: Throwable) {
                if (e is VirtualMachineError) throw e
            }
        }
    }
}

suspend fun MessageChannelBehavior.createLongMessage(text: String, attachments: List<DownloadedFileInfo> = emptyList(), reference: Snowflake? = null) {
    var content = text.trim()
    val parts = mutableListOf<String>()

    while (true) {
        content = content.trim()
        if (content.isEmpty()) {
            break
        }
        if (content.length <= 2000) {
            parts.add(content)
            break
        } else {
            val lineBreak = content.lastIndexOf('\n', 2000 - 1)
            if (lineBreak == -1) {
                val spaceBreak = content.lastIndexOf(' ', 2000 - 1)
                if (spaceBreak == -1) {
                    parts.add(content.substring(0, 2000))
                    content = content.substring(2000)
                } else {
                    parts.add(content.substring(0, spaceBreak))
                    content = content.substring(spaceBreak + 1)
                }
            } else {
                parts.add(content.substring(0, lineBreak))
                content = content.substring(lineBreak + 1)
            }
        }
    }

    for ((index, part) in parts.withIndex()) {
        check(part.length > 0) { "Part is empty" }
        check(part.length <= 2000) { "Part is too long: ${part.length}" }
        createMessage {
            this.content = part
            if (reference != null) {
                this.messageReference = reference
            }
            if (index == parts.size - 1) {
                for (attachment in attachments) {
                    addFile(attachment.fileName, ChannelProvider(attachment.size.toLong()) { attachment.file.readChannel() })
                }
            }
        }
    }
}
