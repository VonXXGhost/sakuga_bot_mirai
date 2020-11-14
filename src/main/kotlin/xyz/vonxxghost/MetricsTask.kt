package xyz.vonxxghost

import com.google.gson.Gson
import mu.KotlinLogging
import org.jsoup.Jsoup
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

val logger = KotlinLogging.logger("MetricsTask")

val CONFIG = getConfig()

data class BotMetrics(val isOnline: Boolean)

object BotMetricsTask : TimerTask() {

    private var isSended = false

    override fun run() {
        try {
            val resp = Jsoup
                .connect("http://${CONFIG[NetSpec.botHostname]}:${CONFIG[NetSpec.port]}/metrics")
                .ignoreContentType(true)
                .get()
                .text()
            val json = Gson().fromJson(resp, BotMetrics::class.java)
            logger.debug { json.toString() }
            if (json == null || !json.isOnline) {
                sendMail("Bot貌似挂了", resp)
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
