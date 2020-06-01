package xyz.vonxxghost

import net.mamoe.mirai.Bot
import net.mamoe.mirai.network.NoStandardInputForCaptchaException
import net.mamoe.mirai.utils.DefaultLoginSolver
import net.mamoe.mirai.utils.LoginSolver

class NotifyLoginSolver : LoginSolver() {

    private val delegate = DefaultLoginSolver({ readLine() ?: throw NoStandardInputForCaptchaException(null) })

    override suspend fun onSolvePicCaptcha(bot: Bot, data: ByteArray): String? {
        sendMailToSelf("bot需要验证码", data.toString())
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
