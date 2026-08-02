package cn.qfys521.xiaoming.qqbot.contact

import cn.chuanwise.xiaoming.bot.XiaoMingBot
import cn.chuanwise.xiaoming.contact.contact.XiaoMingContact
import cn.chuanwise.xiaoming.contact.message.Message
import cn.qfys521.qqbot.QQBot
import cn.qfys521.xiaoming.qqbot.id.QqIdMapper
import cn.qfys521.xiaoming.qqbot.message.QqMessage
import kotlinx.coroutines.runBlocking
import net.mamoe.mirai.contact.Contact
import net.mamoe.mirai.message.data.Image
import net.mamoe.mirai.message.data.MessageChain
import net.mamoe.mirai.utils.ExternalResource
import org.slf4j.LoggerFactory
import java.lang.reflect.Proxy
import java.util.Collections
import java.util.Optional
import java.util.function.Predicate

/**
 * QQ 官方机器人适配器针对小明联系人会话（[XiaoMingContact]）的实现类。
 *
 * 包装 QQ 群（Group）或 C2C 单聊用户会话，支持处理来自小明框架和插件的文本发信请求。
 *
 * @property bot 小明机器人内核实例
 * @property qqBot 官方 QQBot Kotlin SDK 客户端实例
 * @property contactId 会话 OpenID（如果是群聊为 `groupOpenid`，私聊为 `userOpenid`）
 * @property contactName 会话显示名或别称
 * @property isDirect 是否为单聊（C2C），`false` 表示群聊
 * @property lastMessageId 最后收到的一条触发响应的官方 `msg_id`，用于被动回复
 *
 * @author qfys521
 * @since 1.0.0
 */
class QqContact(
    private var bot: XiaoMingBot,
    val qqBot: QQBot,
    val contactId: String,
    private val contactName: String = contactId,
    val isDirect: Boolean = false,
    var lastMessageId: String = ""
) : XiaoMingContact<Contact> {

    private val logger = LoggerFactory.getLogger("QqContact-$contactId")
    private val tags: MutableSet<String> = mutableSetOf("all")
    private val proxyContact: Contact = createFakeMiraiContact(contactId, contactName)

    override fun getXiaoMingBot(): XiaoMingBot = bot

    override fun setXiaoMingBot(bot: XiaoMingBot) {
        this.bot = bot
    }

    override fun getAliasAndCode(): String = "$contactName ($contactId)"

    override fun getMiraiContact(): Contact = proxyContact

    override fun getCode(): Long {
        return QqIdMapper.toLongId(contactId)
    }

    override fun getCodeString(): String = contactId

    override fun getName(): String = contactName

    override fun getAlias(): String = contactName

    override fun getAvatarUrl(): String = ""

    private var msgSeq = 1

    override fun sendMessage(messages: MessageChain?): Optional<Message> {
        val text = messages?.contentToString() ?: return Optional.empty()
        if (text.isBlank()) return Optional.empty()

        // Replace any mapped virtual Long IDs back to OpenID mentions
        val idRegex = "\\b9000000000000000\\d{3,}\\b".toRegex()
        val oldIdRegex = "\\b10000000\\d{3,}\\b".toRegex()
        var processedText = idRegex.replace(text) { match ->
            val virtualId = match.value.toLongOrNull()
            if (virtualId != null) {
                val openId = cn.qfys521.xiaoming.qqbot.id.QqIdMapper.toOpenId(virtualId)
                if (openId != null) "<@!$openId>" else match.value
            } else {
                match.value
            }
        }
        processedText = oldIdRegex.replace(processedText) { match ->
            val virtualId = match.value.toLongOrNull()
            if (virtualId != null) {
                val openId = cn.qfys521.xiaoming.qqbot.id.QqIdMapper.toOpenId(virtualId)
                if (openId != null) "<@!$openId>" else match.value
            } else {
                match.value
            }
        }


        return try {
            val response = runBlocking {
                val req = cn.qfys521.qqbot.model.message.SendMessageRequest(
                    content = null,
                    msgType = 2,
                    markdown = cn.qfys521.qqbot.model.message.MessageMarkdown(content = processedText),
                    msgId = lastMessageId.ifEmpty { null },
                    msgSeq = if (lastMessageId.isNotEmpty()) msgSeq++ else null
                )
                if (isDirect) {
                    qqBot.api.sendC2CMessage(userOpenId = contactId, request = req)
                } else {
                    qqBot.api.sendGroupMessage(groupOpenId = contactId, request = req)
                }
            }
            logger.info("[发送消息] -> {} ({}): {}", contactName, contactId, processedText)
            val sentMsg = cn.qfys521.xiaoming.qqbot.message.QqMessage(bot, processedText, System.currentTimeMillis(), response.id ?: "")
            java.util.Optional.of(sentMsg)
        } catch (e: Exception) {
            logger.error("向 QQ 会话($contactId) 发送消息失败: ${e.message}", e)
            Optional.empty()
        }
    }

    override fun nextMessage(timeout: Long, filter: Predicate<Message>?): Optional<Message> {
        val f = filter ?: Predicate { true }
        return bot.contactManager.nextMessageEvent(timeout) { event ->
            event.user.contact.code == this.code && f.test(event.message)
        }.map { it.message }
    }

    override fun uploadImage(resource: ExternalResource?): Image {
        throw UnsupportedOperationException("QQ 官方机器人网关暂不支持直接通过 Mirai ExternalResource 上传图片")
    }

    override fun getTags(): Set<String> = Collections.unmodifiableSet(tags)

    override fun getOriginalTags(): Set<String> = setOf("all")

    override fun flush() {}

    override fun addTag(tag: String): Boolean = tags.add(tag)

    override fun hasTag(tag: String): Boolean = tags.contains(tag)

    override fun removeTag(tag: String?): Boolean = tag != null && tags.remove(tag)

    companion object {
        private fun createFakeMiraiContact(idStr: String, name: String): Contact {
            val numId = QqIdMapper.toLongId(idStr)
            return Proxy.newProxyInstance(
                Contact::class.java.classLoader,
                arrayOf(Contact::class.java)
            ) { _, method, _ ->
                when (method.name) {
                    "getId" -> numId
                    "getName", "getNick", "toString" -> name
                    else -> {
                        when (method.returnType) {
                            Boolean::class.java -> false
                            Long::class.java, Long::class.javaObjectType -> 0L
                            Int::class.java, Int::class.javaObjectType -> 0
                            String::class.java -> ""
                            else -> null
                        }
                    }
                }
            } as Contact
        }
    }
}
