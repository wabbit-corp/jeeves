package jeeves

import com.aallam.openai.api.image.ImageSize
import com.aallam.openai.api.image.Quality
import com.aallam.openai.api.model.ModelId
import jeeves.society.Credits

object OpenAIPricing {
    fun completionCost(modelId: ModelId, inputTokens: Int, outputTokens: Int): Credits {
        // gpt-4o
        //$5.00 /
        //1M tokens
        //$15.00 /
        //1M tokens
        //gpt-4o-2024-05-13
        //$5.00 /
        //1M tokens
        //$15.00 /
        //1M tokens

        when (modelId.id) {
            "gpt-4o" ->
                return Credits.fromRealUSD(5.00 * (inputTokens / 1_000_000.0)) +
                        Credits.fromRealUSD(15.0 * (outputTokens / 1_000_000.0))
            "gpt-4o-2024-05-13" ->
                return Credits.fromRealUSD(5.00 * (inputTokens / 1_000_000.0)) +
                        Credits.fromRealUSD(15.0 * (outputTokens / 1_000_000.0))
            else -> {
                error("Unsupported model: ${modelId.id}")
            }
        }
    }

    fun dalleCost(modelId: ModelId, size: ImageSize, quality: Quality): Credits {
        when (modelId.id) {
            "dall-e-3" -> when (quality.value) {
                "standard" -> when (size.size) {
                    "1024x1024" -> return Credits.fromRealUSD(0.040)
                    "1024x1792", "1792x1024" -> return Credits.fromRealUSD(0.080)
                    else -> error("Unsupported size: ${size.size}")
                }

                "hd" -> when (size.size) {
                    "1024x1024" -> return Credits.fromRealUSD(0.080)
                    "1024x1792", "1792x1024" -> return Credits.fromRealUSD(0.120)
                    else -> error("Unsupported size: ${size.size}")
                }
                else -> error("Unsupported quality: ${quality.value}")
            }
            "dall-e-2" -> when (size.size) {
                "256x256" -> return Credits.fromRealUSD(0.016)
                "512x512" -> return Credits.fromRealUSD(0.018)
                "1024x1024" -> return Credits.fromRealUSD(0.020)
                else -> error("Unsupported size: ${size.size}")
            }
            else -> error("Unsupported model: ${modelId.id}")
        }
    }
}
