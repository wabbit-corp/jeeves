package jeeves.tools

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import jeeves.MessageContext
import jeeves.society.Credits
import jeeves.society.ToolModule
import jeeves.society.ToolResponse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import jeeves.society.Doc

class Weather(val httpClient: HttpClient): ToolModule<Weather.Request>(Request.serializer()) {
    override val toolName = "Current Weather"
    override val description: String = "You can also use `get_current_weather` to get the current weather in a location."
    override suspend fun contextualDescription(context: MessageContext) = description

    @Serializable sealed interface Request {
        @Doc("Get the current weather in a location.")
        @SerialName("GetCurrentWeather")
        @Serializable data class GetCurrentWeather(
            val longitude: Double,
            val latitude: Double
        ): Request
    }

    override suspend fun estimateCost(context: MessageContext, req: Request?): Credits =
        Credits.MinToolCost

    override suspend fun execute(context: MessageContext, req: Request): ToolResponse {
        when (req) {
            is Request.GetCurrentWeather -> {
                val longitude = req.longitude
                val latitude = req.latitude

                // Call the weather API
                var apiUrl = "https://api.open-meteo.com/v1/forecast?latitude=${latitude}&longitude=${longitude}"
                apiUrl += "&current=temperature_2m,apparent_temperature,is_day,precipitation,rain,showers,snowfall,cloud_cover,wind_speed_10m,wind_gusts_10m"
                // # {
                //            #     "latitude": 43.646603,
                //            #     "longitude": -79.38269,
                //            #     "generationtime_ms": 0.0940561294555664,
                //            #     "utc_offset_seconds": 0,
                //            #     "timezone": "GMT",
                //            #     "timezone_abbreviation": "GMT",
                //            #     "elevation": 97.0,
                //            #     "current_units": {
                //            #     "time": "iso8601",
                //            #     "interval": "seconds",
                //            #     "temperature_2m": "\u00b0C",
                //            #     "apparent_temperature": "\u00b0C",
                //            #     "is_day": "",
                //            #     "precipitation": "mm",
                //            #     "rain": "mm",
                //            #     "showers": "mm",
                //            #     "snowfall": "cm",
                //            #     "cloud_cover": "%",
                //            #     "wind_speed_10m": "km/h",
                //            #     "wind_gusts_10m": "km/h"
                //            #     },
                //            #     "current": {
                //            #     "time": "2024-03-21T20:30",
                //            #     "interval": 900,
                //            #     "temperature_2m": -1.5,
                //            #     "apparent_temperature": -8.2,
                //            #     "is_day": 1,
                //            #     "precipitation": 0.0,
                //            #     "rain": 0.0,
                //            #     "showers": 0.0,
                //            #     "snowfall": 0.0,
                //            #     "cloud_cover": 100,
                //            #     "wind_speed_10m": 21.9,
                //            #     "wind_gusts_10m": 34.9
                //            #     }
                //            # }
                val response = httpClient.get(apiUrl)

                val json = Json.decodeFromString<JsonObject>(response.bodyAsText())

                val result = JsonObject(mapOf(
                    "latitude" to json["latitude"]!!,
                    "longitude" to json["longitude"]!!,
                    "temperature_2m" to JsonPrimitive("${json["current"]!!.jsonObject["temperature_2m"]} ${json["current_units"]!!.jsonObject["temperature_2m"]}"),
                    "apparent_temperature" to JsonPrimitive("${json["current"]!!.jsonObject["apparent_temperature"]} ${json["current_units"]!!.jsonObject["apparent_temperature"]}"),
                    "is_day" to JsonPrimitive(json["current"]!!.jsonObject["is_day"]!!.jsonPrimitive.int == 1),
                    "precipitation" to JsonPrimitive("${json["current"]!!.jsonObject["precipitation"]} ${json["current_units"]!!.jsonObject["precipitation"]}"),
                    "rain" to JsonPrimitive("${json["current"]!!.jsonObject["rain"]} ${json["current_units"]!!.jsonObject["rain"]}"),
                    "showers" to JsonPrimitive("${json["current"]!!.jsonObject["showers"]} ${json["current_units"]!!.jsonObject["showers"]}"),
                    "snowfall" to JsonPrimitive("${json["current"]!!.jsonObject["snowfall"]} ${json["current_units"]!!.jsonObject["snowfall"]}"),
                    "cloud_cover" to JsonPrimitive("${json["current"]!!.jsonObject["cloud_cover"]} ${json["current_units"]!!.jsonObject["cloud_cover"]}"),
                    "wind_speed_10m" to JsonPrimitive("${json["current"]!!.jsonObject["wind_speed_10m"]} ${json["current_units"]!!.jsonObject["wind_speed_10m"]}"),
                    "wind_gusts_10m" to JsonPrimitive("${json["current"]!!.jsonObject["wind_gusts_10m"]} ${json["current_units"]!!.jsonObject["wind_gusts_10m"]}"),
                ))

                return ToolResponse.Success(result, cost = Credits.MinToolCost)
            }
        }
    }
}
