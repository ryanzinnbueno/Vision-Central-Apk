import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString

@Serializable
data class Tv(val rotacao: String? = null)

fun main() {
    try {
        val tv = Json { ignoreUnknownKeys = true }.decodeFromString<Tv>("""{"rotacao": 90}""")
        println("Success: $tv")
    } catch (e: Exception) {
        println("Error: ${e.message}")
    }
}
