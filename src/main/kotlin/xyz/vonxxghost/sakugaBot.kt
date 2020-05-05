package xyz.vonxxghost

import com.google.gson.Gson
import net.mamoe.mirai.Bot
import net.mamoe.mirai.alsoLogin
import net.mamoe.mirai.event.subscribeMessages
import net.mamoe.mirai.join
import net.mamoe.mirai.message.data.MessageChain
import net.mamoe.mirai.message.data.PlainText
import org.jsoup.Jsoup
import java.time.LocalDateTime

data class SakugaTag(val type: Int, val name: String, val main_name: String)
data class SakugaWeibo(val weibo_id: String, val img_url: String, val weibo_url: String)
data class SakugaPost(val id: Long, val source: String?, val tags: List<SakugaTag>, val weibo: SakugaWeibo?)

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
    println(this.toString())
    val content = this[PlainText].content
    println(content)
    val results = Regex("(?<=post/show/)(\\d+)").findAll(content)
    return results.map { it.value }
}


fun geneReplyText(message: MessageChain): String {
    val replyText = StringBuilder()

    val postIDs = message.getPostIDs()
    val posts = getBotData(postIDs)
    for (post in posts) {
        if (post.weibo != null) {
            val copyright = post.tags.filter { it.type == 3 }.joinToString("，") { it.main_name }
            val artist = post.tags.filter { it.type == 1 }.joinToString("，") { it.main_name }
            replyText.append("${post.id}:\n$copyright ${post.source}\n$artist\n${post.weibo.img_url}\n")
        }
    }
    return replyText.trimEnd().toString()
}

suspend fun main() {
    val qqId = 1L
    val password = ""
    val miraiBot = Bot(qqId, password).alsoLogin()

    miraiBot.subscribeMessages {

        finding(Regex("sakugabooru.com/post/show/\\d+")) {
            reply(geneReplyText(message))
        }
    }

    miraiBot.join()
}
