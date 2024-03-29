package xyz.vonxxghost

import com.google.gson.Gson
import io.javalin.Javalin
import io.ktor.client.request.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import mu.KotlinLogging
import net.mamoe.mirai.BotFactory
import net.mamoe.mirai.alsoLogin
import net.mamoe.mirai.contact.Contact.Companion.sendImage
import net.mamoe.mirai.contact.nameCardOrNick
import net.mamoe.mirai.event.*
import net.mamoe.mirai.event.events.BotInvitedJoinGroupRequestEvent
import net.mamoe.mirai.event.events.ImageUploadEvent
import net.mamoe.mirai.event.events.NewFriendRequestEvent
import net.mamoe.mirai.message.data.MessageChain
import net.mamoe.mirai.message.data.MessageSource.Key.quote
import net.mamoe.mirai.utils.BotConfiguration
import net.mamoe.mirai.utils.DeviceInfo.Companion.loadAsDeviceInfo
import net.mamoe.mirai.utils.ExternalResource.Companion.toExternalResource
import java.io.File
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.random.Random

const val HELP_MSG = """使用指南
在群里发送sakugabooru的稿件链接，本账号将自动搜索是否存在微博gif数据，如果存在则发送gif地址和图片到群里，不存在就无反应。
出于账号安全考虑，随机作画功能暂时关闭。
暂无设置功能，不想看到禁言即可。"""

val log = KotlinLogging.logger("sakugaBotMain")
var leastID = 131261L
var lastUpdateDay = LocalDate.of(2020, 1, 1)!!

var limitTime = System.currentTimeMillis() / 60000
val limitCounter = AtomicInteger(0)

val httpClient = HttpRequest.request

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

suspend fun updateLeastPostId() {
    val resp = httpClient
        .get<SakugaPostList>("https://sakugabot.pw/api/posts/?format=json")
    leastID = resp.results.first().id
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
            var result: SakugaPost
            runBlocking {
                result = httpClient.get(
                    getPostFromBotAPI(it)
                )
            }
            if (result.weibo == null) {
                log.info { "Post $it 微博数据不存在" }
            }
            return@map result
        } catch (e: Exception) {
            log.error(e) { "getBotData ERROR" }
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
        log.info { "Post $id 微博数据不存在" }
        return null
    }

    val copyright = post.copyright()
    val artist = post.artist()
    urls.add(post.weibo.img_url)

    val replyText = "${post.sakugabooru_url}\n$copyright ${post.source} $artist\n${post.weibo.img_url}"

    return TextAndUrls(replyText, urls.take(1))
}

fun riskProcessUrl(url: String): String {
    return url.trim()
        .replaceFirst(".", "\u200b.")
        .replaceFirst(Regex("(?<=[^\u200b])\\."), "\u200b.")
        .replace(Regex("^https?://"), "")
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
            replyText.append(
                "${post.id}:\n$copyright ${post.source} $artist\n" +
                        "${riskProcessUrl(post.weibo.img_url)}\n"
            )
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

    val bot = BotFactory.newBot(qqId, password) {
        botLoggerSupplier = { SkgBotLogger("sakugaBotMirai") }
        networkLoggerSupplier = { SkgBotLogger("sakugaBotMiraiNet") }
        protocol = PROTOCOL_MAP[config[QqSpec.protocol]] ?: error("ERROR protocol")
        deviceInfo =
            {
                File("device.json")
                    .loadAsDeviceInfo(Json { prettyPrint = true })
            }
    }.alsoLogin()

    val server = Javalin.create().start(config[NetSpec.port])
    server.get("/metrics") { ctx ->
        run {
            val metrics = mapOf("isOnline" to bot.isOnline)
            ctx.result(Gson().toJson(metrics))
            ctx.contentType("application/json")
        }
    }

    bot.eventChannel.subscribeGroupMessages {

        finding(Regex("sakugabooru.com/post/show/\\d+")) {
            if (!checkLimit()) {
                return@finding
            }

            val dat = geneReplyTextAndPicUrl(message)

            if (dat.text.isNotEmpty()) {
                val sentMsg = subject.sendMessage(dat.text)
                for (url in dat.urls) {
                    val result = httpClient.get<ByteArray> {
                        url(url)
                    }
                    val uploadedImg = subject.uploadImage(result.toExternalResource())
                    // 引用回复如果纯图片不会被解析成引用，加个不可见字符
                    subject.sendMessage(sentMsg.quote().plus("\u200b").plus(uploadedImg))
                }
            }
        }

        atBot {
            if (!checkLimit()) {
                return@atBot
            }

            if ("-h" in message.contentToString()) {
                subject.sendMessage(HELP_MSG)
            }
        }

        always {
            log.info {
                "组[${group.id}][${group.name}]人[${sender.id}][${sender.nameCardOrNick}]: " +
                        message.toString()
            }
        }

//        contains("#随机作画") {
//            if (!checkLimit()) {
//                return@contains
//            }
//
//            if (updateUpdateDay()) {
//                updateLeastPostId() // 每日更新
//            }
//            for (i in 0..5) {
//                val id = getRandomPostId()
//                log.info { "随机尝试id：$id" }
//                val dat = geneReplyTextAndPicUrl(id) ?: continue
//
//                if (dat.text.isNotEmpty()) {
//                    subject.sendMessage(message.quote() + ("Random-" + dat.text))
//                }
//                break
//            }
//        }
    }

    bot.eventChannel.subscribeAlways<ImageUploadEvent.Failed> {
        log.error { "图片上传失败。${it.message}" }
    }

    // 好友验证
    bot.eventChannel.subscribeAlways<NewFriendRequestEvent> {
        it.accept()
        log.info { "添加好友[${it.fromGroup}(${it.fromGroupId})] by ${it.fromId}: ${it.message}" }
    }

    // 群邀请
    bot.eventChannel.subscribeAlways<BotInvitedJoinGroupRequestEvent> {
        it.accept()
        log.info { "被邀请加入群${it.groupId} by ${it.invitorId}" }
    }

    bot.eventChannel.subscribeFriendMessages {
        always {
            subject.sendMessage("本bot现不支持私聊信息处理。")
        }
    }

    bot.eventChannel.subscribeGroupTempMessages(
        EmptyCoroutineContext,
        ConcurrencyKind.CONCURRENT,
        EventPriority.MONITOR
    ) {
        contains("随机作画") {
            subject.sendMessage("本bot现不支持私聊信息处理。")
        }
    }

    bot.join()
}
