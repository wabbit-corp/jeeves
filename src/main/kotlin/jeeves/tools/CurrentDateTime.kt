package jeeves.tools

import jeeves.MessageContext
import jeeves.society.ToolModule
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

class CurrentDateTime : ToolModule.NoFunctions() {
    override val toolName: String = "Current Date and Time"
    override val description: String = """
        Provides the current date and time in New York City. 
        It is your responsibility to provide the date and time in the user's timezone when answering questions.
    """.trimIndent()

    override suspend fun contextualDescription(context: MessageContext): String {
        val newYorkDT = ZonedDateTime.now(ZoneId.of("America/New_York"))
        val newYorkDate = newYorkDT.format(DateTimeFormatter.ofPattern("EEEE, MMMM dd, yyyy"))
        val newYorkTime = newYorkDT.format(DateTimeFormatter.ofPattern("HH:mm:ss"))

        return """
            Current date and time in New York City is {{new_york_date_str}} and time is {{new_york_time_str}}. 
            When answering questions about the date and time, provide it in human readable form. 
            Assume the users are in New York unless otherwise specified. 
            If you are asked about the time in a different location, provide the time in that location based on 
            the timezone and UTC offset.
        """.trimIndent().trim()
            .replace("{{new_york_date_str}}", newYorkDate)
            .replace("{{new_york_time_str}}", newYorkTime)
    }
}
