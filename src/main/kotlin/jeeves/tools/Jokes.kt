package jeeves.tools

import jeeves.society.Credits
import jeeves.MessageContext
import jeeves.society.ToolModule
import jeeves.society.ToolResponse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import jeeves.society.Doc
import java.io.File
import java.util.*
import kotlin.collections.ArrayDeque
import kotlin.math.pow

class Jokes(val jokes: List<RedditJoke>, val scores: DoubleArray, val nsfwChecker: NSFWChecker) : ToolModule<Jokes.Request>(Request.serializer()) {
    override val toolName: String = "Jokes"
    override val description: String = """
        You can get a random joke from Reddit using the `get_joke` function.
        If the joke is not up to your standards, you can always ask for another one.
    """.trimIndent()
    override suspend fun contextualDescription(context: MessageContext): String = description

    @Serializable sealed interface Request {
        @Doc("Get a random reddit joke.")
        @SerialName("GetJoke")
        @Serializable data object GetJoke : Request
    }

    private val SEEN_JOKE_COUNT = 128
    private val recentlySeen = ArrayDeque<Int>()
    private val recentlySeenSet = mutableSetOf<Int>()
    private val random = SplittableRandom()

    override suspend fun estimateCost(context: MessageContext, req: Request?): Credits {
        return Credits.MinToolCost
    }

    override suspend fun execute(context: MessageContext, req: Request): ToolResponse {
        when (req) {
            is Request.GetJoke -> {
                val joke = getJoke()
                return ToolResponse.Success(JsonObject(mapOf(
                    "opening" to JsonPrimitive(joke.title),
                    "punchline" to JsonPrimitive(joke.body),
                    "reddit_score" to JsonPrimitive(joke.score)
                )), cost = Credits.MinToolCost)
            }
        }
    }

    fun sample(weights: DoubleArray, random: SplittableRandom): Int {
        val f = random.nextDouble()
        var runningSum = 0.0
        for (i in weights.indices) {
            runningSum += weights[i]
            if (f < runningSum) {
                return i
            }
        }
        return weights.size - 1
    }

    fun getJoke(): RedditJoke {
        var index: Int = -1
        if (recentlySeen.size >= SEEN_JOKE_COUNT) {
            index = recentlySeen.removeFirst()
            recentlySeenSet.remove(index)
        } else {
            while (true) {
                index = sample(scores, random)
                val joke = jokes[index]
                if (nsfwChecker.isNSFW(joke.title) || nsfwChecker.isNSFW(joke.body)) {
                    continue
                }
                if (index !in recentlySeenSet) {
                    break
                }
            }
        }

        val joke = jokes[index]
        recentlySeen.add(index)
        recentlySeenSet.add(index)
        return joke
    }

    // {
    //        "body": "Pizza doesn't scream when you put it in the oven .\n\nI'm so sorry.",
    //        "id": "5tz4dd",
    //        "score": 0,
    //        "title": "What's the difference between a Jew in Nazi Germany and pizza ?"
    //    },
    @Serializable
    class RedditJoke(
        val body: String,
        val id: String,
        val score: Int,
        val title: String
    )

    companion object {
        fun loadFromFile(path: String, nsfwChecker: NSFWChecker): Jokes {
            val list = Json.decodeFromString<List<RedditJoke>>(File(path).readText())

            val scores = list.map { it.score + 1.0 }.toDoubleArray()
            for (i in scores.indices) {
                scores[i] = scores[i].pow(1.0 / 2)
            }
            val sum = scores.sum()
            for (i in scores.indices) {
                scores[i] /= sum
            }

            return Jokes(list, scores, nsfwChecker)
        }
    }
}
