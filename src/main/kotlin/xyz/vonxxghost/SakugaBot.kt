package xyz.vonxxghost

import com.google.gson.Gson
import mu.KotlinLogging
import net.mamoe.mirai.Bot
import net.mamoe.mirai.alsoLogin
import net.mamoe.mirai.contact.nameCardOrNick
import net.mamoe.mirai.event.subscribeMessages
import net.mamoe.mirai.join
import net.mamoe.mirai.message.GroupMessageEvent
import net.mamoe.mirai.message.data.MessageChain
import net.mamoe.mirai.utils.secondsToMillis
import org.jsoup.Jsoup
import java.net.URL

const val HELP_MSG = """使用指南
在群里发送sakugabooru的稿件链接，本账号将自动搜索是否存在微博gif数据，如果存在则发送gif地址到群里，不存在就无反应。
已支持图片发送功能，但经测试上传失败率较高，所以随缘。
暂无设置功能，不想看到禁言即可。现处于新程序测试阶段，问题较多见怪莫怪。"""

val log = KotlinLogging.logger("sakugaBotMain")

data class SakugaTag(val type: Int, val name: String, val main_name: String)
data class SakugaWeibo(val weibo_id: String, val img_url: String, val weibo_url: String)
data class SakugaPost(val id: Long, val source: String?, val tags: List<SakugaTag>, val weibo: SakugaWeibo?)

data class TextAndUrls(val text: String, val urls: List<String>)

fun getPostFromBotAPI(postID: String): String {
    return "https://sakugabot.pw/api/posts/$postID/?format=json"
}

fun getBotData(postIDs: Sequence<String>): Sequence<SakugaPost> {

    return postIDs.map {
        val resp = Jsoup
            .connect(getPostFromBotAPI(it))
            .ignoreContentType(true)
            .get()
            .text()
        return@map Gson().fromJson(resp, SakugaPost::class.java)
    }
}

fun MessageChain.getPostIDs(): Sequence<String> {
    val content = this.contentToString()
    val results = Regex("(?<=post/show/)(\\d+)").findAll(content)
    return results.map { it.value }.distinct()
}


fun geneReplyTextAndPicUrl(message: MessageChain): TextAndUrls {
    val replyText = StringBuilder()
    val urls = mutableListOf<String>()

    val postIDs = message.getPostIDs()
    val posts = getBotData(postIDs)
    for (post in posts) {
        if (post.weibo != null) {
            val copyright = post.tags.filter { it.type == 3 }.joinToString("，") { it.main_name }
            val artist = post.tags.filter { it.type == 1 }.joinToString("，") { it.main_name }
            urls.add(post.weibo.img_url)
            replyText.append("${post.id}:\n$copyright ${post.source} $artist\n${post.weibo.img_url}\n")
        }
    }

    // gif链接最多只取1个
    return TextAndUrls(replyText.trim().toString(), urls.take(3))
}

suspend fun main(args: Array<String>) {
    val qqId = args[0].toLong()
    val password = args[1]
    val miraiBot = Bot(qqId, password) {
        heartbeatPeriodMillis = 30.secondsToMillis
        botLoggerSupplier = { SkgBotLogger("sakugaBotMirai") }
        networkLoggerSupplier = { SkgBotLogger("sakugaBotMiraiNet") }
    }.alsoLogin()

    miraiBot.subscribeMessages {

        finding(Regex("sakugabooru.com/post/show/\\d+")) {
            val dat = geneReplyTextAndPicUrl(message)

            if (dat.text.isNotEmpty()) {
                reply(dat.text)
            }
            for (url in dat.urls) {
                sendImage(URL(url))
            }
        }

        atBot {
            if ("-h" in message.contentToString()) {
                reply(HELP_MSG)
            }
        }

        always {
            if (this is GroupMessageEvent) {
                log.info {
                    "组[${group.id}][${group.name}]人[${sender.id}][${sender.nameCardOrNick}]: " +
                            message.contentToString()
                }
            }
        }
    }

    miraiBot.join()
}
