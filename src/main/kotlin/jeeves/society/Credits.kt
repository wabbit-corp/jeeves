package jeeves.society

import kotlinx.serialization.Serializable
import kotlin.math.ceil
import kotlin.math.roundToLong

@Serializable data class Credits(val realCost: Long, val userCost: Long) {
    operator fun plus(other: Credits): Credits = Credits(realCost + other.realCost, userCost + other.userCost)
    operator fun minus(other: Credits): Credits = Credits(realCost - other.realCost, userCost - other.userCost)
    operator fun unaryMinus(): Credits = Credits(-realCost, -userCost)

    companion object {
        val Margin = 0.10 // 10%
        val MinToolCost: Credits = Credits(0, 100) // 0.1 cents

        fun fromRealUSD(value: Double): Credits {
            val realCost = (value * 100_000).roundToLong()
            val userCost = ceil(value * 100_000 * (1 + Margin)).roundToLong()
            return Credits(realCost, userCost)
        }
    }
}
