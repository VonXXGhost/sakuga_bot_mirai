package xyz.vonxxghost

import mu.KotlinLogging
import net.mamoe.mirai.utils.MiraiLoggerPlatformBase

class SkgBotLogger(override val identity: String) : MiraiLoggerPlatformBase() {

    private val logger = KotlinLogging.logger(identity)

    override fun debug0(message: String?) {
        logger.debug { message }
    }

    override fun debug0(message: String?, e: Throwable?) {
        logger.debug(e) { message }
    }

    override fun error0(message: String?) {
        logger.error { message }
    }

    override fun error0(message: String?, e: Throwable?) {
        logger.error(e) { message }
    }

    override fun info0(message: String?) {
        logger.info { message }
    }

    override fun info0(message: String?, e: Throwable?) {
        logger.info(e) { message }
    }

    override fun verbose0(message: String?) {
        logger.trace { message }
    }

    override fun verbose0(message: String?, e: Throwable?) {
        logger.trace(e) { message }
    }

    override fun warning0(message: String?) {
        logger.warn { message }
    }

    override fun warning0(message: String?, e: Throwable?) {
        logger.warn(e) { message }
    }
}
