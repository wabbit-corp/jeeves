package jeeves.society

import dev.kord.core.event.message.MessageCreateEvent
import jeeves.MessageContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialInfo
import one.wabbit.parsing.charset.CharSet
import one.wabbit.parsing.charset.Topology
import one.wabbit.parsing.grammars.Simple
import one.wabbit.parsing.grammars.SimpleInput
import one.wabbit.parsing.grammars.SimpleResult

@OptIn(ExperimentalSerializationApi::class)
@Target(AnnotationTarget.CLASS)
@SerialInfo
annotation class Requires(val value: String)
// userId=bigbuxchungus && inDM

sealed interface Requirement {
    data class And(val args: List<Requirement>) : Requirement

    data object IsSuperUser : Requirement
    data class UserId(val id: String) : Requirement
    data object InDirectMessageChannel : Requirement

    infix fun and(other: Requirement): Requirement = when (this) {
        is And -> when (other) {
            is And -> And(args + other.args)
            else -> And(args + other)
        }
        else -> when (other) {
            is And -> And(listOf(this) + other.args)
            else -> And(listOf(this, other))
        }
    }

    fun check(context: MessageContext, superuserIds: Set<String>): Boolean = when (this) {
        is And -> args.all { it.check(context, superuserIds) }
        IsSuperUser -> {
            val result = context.userId in superuserIds
            println("IsSuperUser: ${result}")
            result
        }
        is UserId -> context.userId == id
        InDirectMessageChannel -> {
            val event: MessageCreateEvent = context.primaryEvent
            val result = event.guildId == null
            println("InDirectMessageChannel: $result")
            result
        }
    }

    object Parser : one.wabbit.parsing.grammars.Parser.CharModule() {
        val idFirst = CharSet.letter + CharSet.of('_')
        val idRest = idFirst + CharSet.digit
        val space = CharSet.of(' ')

        val ws = space.ignore.many.ignore
        val superUser by "superUser".ignore.map { IsSuperUser } + ws
        val userId by "userId=".ignore + (idFirst + idRest.many1).string.map { UserId(it) } + ws
        val inDM by "inDM".ignore.map { InDirectMessageChannel } + ws

        val primExpr: one.wabbit.parsing.grammars.Parser<CharSet, Char, Requirement> by delay {
            choice("(".ignore + ws + expr + ")".ignore + ws, userId, superUser, inDM)
        }
        val expr by (primExpr + ws).sepBy1("&&".p + ws) { a, _, b -> a and b }

        val start = ws + expr
    }

    companion object {
        fun parse(s: String): Requirement = with (Topology.charRanges) {
            when (val result = Simple.compile(Parser.start).parseAll(SimpleInput.StringInput(s, 0))) {
                is SimpleResult.Success -> result.value
                is SimpleResult.Failure -> throw IllegalArgumentException(
                    "" +
                            "Failed to parse requirement: ${result.failurePath} at ${result.failedAt}. " +
                            "Expected: ${result.expectedChars}"
                )
            }
        }
    }
}
