package xyz.vonxxghost

import net.mamoe.mirai.Bot
import net.mamoe.mirai.network.NoStandardInputForCaptchaException
import net.mamoe.mirai.utils.DefaultLoginSolver
import net.mamoe.mirai.utils.LoginSolver
import java.util.*

class NotifyLoginSolver : LoginSolver() {

    private val delegate = DefaultLoginSolver({ readLine() ?: throw NoStandardInputForCaptchaException(null) })

    override suspend fun onSolvePicCaptcha(bot: Bot, data: ByteArray): String? {
        val encoder = Base64.getEncoder()
        val html = "<img src=\" data:image/png;base64,${encoder.encodeToString(data)}\" />"
        sendMailToSelf("bot需要验证码", html)
        return delegate.onSolvePicCaptcha(bot, data)
    }

    override suspend fun onSolveSliderCaptcha(bot: Bot, url: String): String? {
        sendMailToSelf("bot需要验证码", url)
        return delegate.onSolveSliderCaptcha(bot, url)
    }

    override suspend fun onSolveUnsafeDeviceLoginVerify(bot: Bot, url: String): String? {
        sendMailToSelf("bot需要验证码", url)
        return delegate.onSolveUnsafeDeviceLoginVerify(bot, url)
    }
}
