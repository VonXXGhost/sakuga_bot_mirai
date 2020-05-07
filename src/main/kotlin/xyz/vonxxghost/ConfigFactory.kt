package xyz.vonxxghost

import com.uchuhimo.konf.Config
import com.uchuhimo.konf.ConfigSpec
import com.uchuhimo.konf.source.yaml


object QqSpec : ConfigSpec() {
    val id by required<Long>()
    val password by required<String>()
}

object MailSpec : ConfigSpec() {
    val enabled by optional(false)
    val hostName by required<String>()
    val smtpPort by required<Int>()
    val username by required<String>()
    val password by required<String>()
    val emailAddress by required<String>()
}

fun getConfig(): Config {
    return Config {
        addSpec(QqSpec)
        addSpec(MailSpec)
    }
        .from.yaml.file("botConfig.yml")
        .from.systemProperties()
}

