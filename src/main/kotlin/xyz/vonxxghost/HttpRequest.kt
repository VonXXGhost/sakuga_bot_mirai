package xyz.vonxxghost

import io.ktor.client.*
import io.ktor.client.engine.apache.*
import io.ktor.client.features.json.*
import io.ktor.client.features.logging.*

/**
 * @author VonXXGhost
 * @date 2021/1/30 上午 11:03
 */
object HttpRequest {
    val request = HttpClient(Apache) {
        engine {
            followRedirects = true
            connectTimeout = 15_000
            socketTimeout = 30_000
            connectionRequestTimeout = 15_000

            customizeClient {
                setMaxConnPerRoute(10)
                setMaxConnTotal(50)
            }
        }

        install(JsonFeature) {
            serializer = GsonSerializer {
                setPrettyPrinting()
                disableHtmlEscaping()
            }
        }

        install(Logging) {
            logger = Logger.DEFAULT
            level = LogLevel.INFO
        }

    }
}
