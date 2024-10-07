package jeeves.tools

import java.io.File

class NSFWChecker(val set: Set<String>) {
    fun isNSFW(text: String): Boolean {
        return set.any { text.contains(it, ignoreCase = true) }
    }

    companion object {
        fun loadFromFile(path: String): NSFWChecker {
            val badWords = File(path).readLines().map { it.trim() }
            return NSFWChecker(badWords.toSet())
        }
    }
}
