package xyz.vonxxghost

import org.apache.commons.mail.DefaultAuthenticator
import org.apache.commons.mail.SimpleEmail

fun sendMailToSelf(title: String, msg: String) {
    val config = getConfig()
    val email = SimpleEmail()
    email.hostName = config[MailSpec.hostName]
    email.setSmtpPort(config[MailSpec.smtpPort])
    email.setAuthenticator(DefaultAuthenticator(config[MailSpec.username], config[MailSpec.password]))
    email.isSSLOnConnect = true
    val address = config[MailSpec.emailAddress]
    email.setFrom(address)
    email.subject = title
    email.setMsg(msg)
    email.addTo(address)
    email.send()
}
