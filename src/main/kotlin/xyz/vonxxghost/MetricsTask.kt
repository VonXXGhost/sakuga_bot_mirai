package xyz.vonxxghost

import io.ktor.client.request.*
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

data class BotMetrics(val isOnline: Boolean)

val logger = KotlinLogging.logger("MetricsTask")

val CONFIG = getConfig()

object BotMetricsTask : TimerTask() {

    private var isSended = false

    override fun run() {
        val httpClient = HttpRequest.request
        try {
            runBlocking {
                val resp = httpClient
                    .get<BotMetrics>("http://${CONFIG[NetSpec.botHostname]}:${CONFIG[NetSpec.port]}/metrics")
                logger.debug { resp }
                if (!resp.isOnline) {
                    sendMail("Bot貌似挂了", resp.toString())
                } else if (resp.isOnline) {
                    isSended = false
                }
            }
        } catch (e: Exception) {
            logger.error { e }
            sendMail("Bot貌似挂了", e.stackTraceToString())
        }
    }

    private fun sendMail(title: String, msg: String) {
        logger.info { msg }
        if (!isSended) {
            logger.info { "发送邮件" }
            sendMailToSelf(title, msg)
        } else {
            logger.info { "已发送邮件，此次跳过" }
        }
        isSended = true
    }

}

fun main() {
    val executor = Executors.newScheduledThreadPool(1)
    executor.scheduleWithFixedDelay(BotMetricsTask, 0, 1, TimeUnit.HOURS)
    while (true) {
        Thread.sleep(100000)
    }
}
