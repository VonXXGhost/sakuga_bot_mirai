package xyz.vonxxghost

import com.google.gson.Gson
import io.javalin.Javalin
import kotlinx.serialization.json.Json
import mu.KotlinLogging
import net.mamoe.mirai.Bot
import net.mamoe.mirai.alsoLogin
import net.mamoe.mirai.contact.nameCardOrNick
import net.mamoe.mirai.event.events.BotInvitedJoinGroupRequestEvent
import net.mamoe.mirai.event.events.ImageUploadEvent
import net.mamoe.mirai.event.events.NewFriendRequestEvent
import net.mamoe.mirai.event.subscribeAlways
import net.mamoe.mirai.event.subscribeFriendMessages
import net.mamoe.mirai.event.subscribeGroupMessages
import net.mamoe.mirai.event.subscribeTempMessages
import net.mamoe.mirai.join
import net.mamoe.mirai.message.data.MessageChain
import net.mamoe.mirai.utils.BotConfiguration
import net.mamoe.mirai.utils.loadAsDeviceInfo
import org.jsoup.Jsoup
import java.io.File
import java.net.URL
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random

const val HELP_MSG = """使用指南
在群里发送sakugabooru的稿件链接，本账号将自动搜索是否存在微博gif数据，如果存在则发送gif地址到群里，不存在就无反应。
已支持图片发送功能。
发出带有“#随机作画”的信息时会随机回复。
bot仅群组有效，全局消息发送限制6条一分钟，超出后不响应。请不要短期大量占用资源。
暂无设置功能，不想看到禁言即可。"""

val log = KotlinLogging.logger("sakugaBotMain")
var leastID = 131261L
var lastUpdateDay = LocalDate.of(2020, 1, 1)

var limitTime = System.currentTimeMillis() / 60000
val limitCounter = AtomicInteger(0)

data class SakugaTag(val type: Int, val name: String, val main_name: String)
data class SakugaWeibo(val weibo_id: String, val img_url: String, val weibo_url: String)
data class SakugaPost(
    val id: Long,
    val source: String?,
    val tags: List<SakugaTag>,
    val weibo: SakugaWeibo?,
    val sakugabooru_url: String
)

data class SakugaPostSimple(val id: Long)
data class SakugaPostList(val results: List<SakugaPostSimple>)

data class TextAndUrls(val text: String, val urls: List<String>)

fun checkLimit(): Boolean {
    val now = System.currentTimeMillis() / 60000
    log.trace { "limitTime:$limitTime, now:$now, counter:$limitCounter" }
    if (now == limitTime) {
        if (limitCounter.incrementAndGet() > 6) {
            log.info { "超出调用限制。time：$limitTime, counter：$limitCounter" }
            return false
        }
    } else {
        limitTime = now
        limitCounter.set(0)
    }
    return true
}

fun getPostFromBotAPI(postID: String): String =
    "https://sakugabot.pw/api/posts/$postID/?format=json"

fun updateLeastPostId() {
    val resp = Jsoup
        .connect("https://sakugabot.pw/api/posts/?format=json")
        .ignoreContentType(true)
        .get()
        .text()
    val json = Gson().fromJson(resp, SakugaPostList::class.java)
    leastID = json.results.first().id
    log.info { "更新最新ID为$leastID" }
}

fun updateUpdateDay(): Boolean {
    val now = LocalDate.now()
    if (now.isEqual(lastUpdateDay)) {
        return false
    }
    lastUpdateDay = now
    log.info { "更新日期为$lastUpdateDay" }
    return true
}

fun getBotData(postIDs: Sequence<String>): Sequence<SakugaPost?> =
    postIDs.map {
        try {
            val resp = Jsoup
                .connect(getPostFromBotAPI(it))
                .ignoreContentType(true)
                .get()
                .text()
            return@map Gson().fromJson(resp, SakugaPost::class.java)
        } catch (e: Exception) {
            return@map null
        }
    }


fun MessageChain.getPostIDs(): Sequence<String> {
    val content = this.contentToString()
    val results = Regex("(?<=post/show/)(\\d+)").findAll(content)
    return results.map { it.value }.distinct()
}

fun SakugaPost.copyright(): String =
    this.tags.filter { it.type == 3 }.joinToString("，") { it.main_name }

fun SakugaPost.artist(): String =
    this.tags.filter { it.type == 1 }.joinToString("，") { it.main_name }

fun geneReplyTextAndPicUrl(id: Long): TextAndUrls? {
    val urls = mutableListOf<String>()

    val posts = getBotData(sequenceOf(id.toString()))
    val post = posts.first()
    if (post?.weibo == null) {
        log.info { "Post ${post?.id} 微博数据不存在" }
        return null
    }

    val copyright = post.copyright()
    val artist = post.artist()
    urls.add(post.weibo.img_url)

    val replyText = "${post.sakugabooru_url}\n$copyright ${post.source} $artist\n${post.weibo.img_url}"

    return TextAndUrls(replyText, urls.take(1))
}

fun geneReplyTextAndPicUrl(message: MessageChain): TextAndUrls {
    val replyText = StringBuilder()
    val urls = mutableListOf<String>()

    val postIDs = message.getPostIDs()
    val posts = getBotData(postIDs)
    for (post in posts) {
        if (post?.weibo != null) {
            val copyright = post.copyright()
            val artist = post.artist()
            urls.add(post.weibo.img_url)
            replyText.append("${post.id}:\n$copyright ${post.source} $artist\n${post.weibo.img_url}\n")
        } else {
            log.info { "Post ${post?.id} 微博数据不存在" }
        }
    }

    // gif链接最多只取1个
    return TextAndUrls(replyText.trim().toString(), urls.take(1))
}

fun getRandomPostId(): Long {
    var id: Long
    do {
        id = Random.nextLong(28799L, leastID)
    } while (id in 88500..102000) // spam
    return id
}

suspend fun main() {
    val config = getConfig()
    val qqId = config[QqSpec.id]
    val password = config[QqSpec.password]

    val PROTOCOL_MAP = mapOf(
        "pad" to BotConfiguration.MiraiProtocol.ANDROID_PAD,
        "phone" to BotConfiguration.MiraiProtocol.ANDROID_PHONE
    )

    val bot = Bot(qqId, password) {
        botLoggerSupplier = { SkgBotLogger("sakugaBotMirai") }
        networkLoggerSupplier = { SkgBotLogger("sakugaBotMiraiNet") }
        protocol = PROTOCOL_MAP[config[QqSpec.protocol]] ?: error("ERROR protocol")
        deviceInfo =
            {
                File("device.json")
                    .loadAsDeviceInfo(Json { prettyPrint = true })
            }
        loginSolver = NotifyLoginSolver()
    }.alsoLogin()

    val server = Javalin.create().start(config[NetSpec.port])
    server.get("/metrics") { ctx ->
        run {
            val metrics = mapOf("isOnline" to bot.isOnline)
            ctx.result(Gson().toJson(metrics))
        }
    }

    bot.subscribeGroupMessages {

        finding(Regex("sakugabooru.com/post/show/\\d+")) {
            if (!checkLimit()) {
                return@finding
            }

            val dat = geneReplyTextAndPicUrl(message)

            if (dat.text.isNotEmpty()) {
                reply(dat.text)
            }
            for (url in dat.urls) {
                URL(url).openConnection().getInputStream().sendAsImage()
            }
        }

        atBot {
            if (!checkLimit()) {
                return@atBot
            }

            if ("-h" in message.contentToString()) {
                reply(HELP_MSG)
            }
        }

        always {
            log.info {
                "组[${group.id}][${group.name}]人[${sender.id}][${sender.nameCardOrNick}]: " +
                        message.contentToString()
            }
        }

        contains("#随机作画") {
            if (!checkLimit()) {
                return@contains
            }

            if (updateUpdateDay()) {
                updateLeastPostId() // 每日更新
            }
            for (i in 0..5) {
                val id = getRandomPostId()
                log.info { "随机尝试id：$id" }
                val dat = geneReplyTextAndPicUrl(id) ?: continue

                if (dat.text.isNotEmpty()) {
                    quoteReply("Random-" + dat.text)
                }
                break
            }
        }
    }

    bot.subscribeAlways<ImageUploadEvent.Failed> {
        log.error { "图片上传失败。${it.message}" }
    }

    // 好友验证
    bot.subscribeAlways<NewFriendRequestEvent> {
        it.accept()
        log.info { "添加好友[${it.fromGroup}(${it.fromGroupId})] by ${it.fromId}: ${it.message}" }
    }

    // 群邀请
    bot.subscribeAlways<BotInvitedJoinGroupRequestEvent> {
        it.accept()
        log.info { "被邀请加入群${it.groupId} by ${it.invitorId}" }
    }

    bot.subscribeFriendMessages {
        always {
            reply("本bot现不支持私聊信息处理。")
        }
    }

    bot.subscribeTempMessages {
        contains("随机作画") {
            reply("本bot现不支持私聊信息处理。")
        }
    }

    bot.join()
}
