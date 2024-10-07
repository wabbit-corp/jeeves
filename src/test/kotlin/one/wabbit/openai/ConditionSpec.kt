package one.wabbit.openai

import jeeves.society.Requirement
import kotlin.test.Test

class ConditionSpec {
    @Test fun test() {
        val requirement = Requirement.parse("(userId=bigbuxchungus && inDM) && superUser")

        println(requirement)
    }
}
