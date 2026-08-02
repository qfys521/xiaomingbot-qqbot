package cn.qfys521.xiaoming.qqbot.user

import cn.chuanwise.toolkit.container.Container
import cn.chuanwise.xiaoming.bot.XiaoMingBot
import cn.chuanwise.xiaoming.contact.contact.XiaoMingContact
import cn.chuanwise.xiaoming.contact.message.Message
import cn.chuanwise.xiaoming.interactor.context.InteractorContext
import cn.chuanwise.xiaoming.property.PropertyType
import cn.chuanwise.xiaoming.recept.ReceptionTask
import cn.chuanwise.xiaoming.recept.Receptionist
import cn.chuanwise.xiaoming.user.XiaoMingUser
import cn.qfys521.xiaoming.qqbot.contact.QqContact
import cn.qfys521.xiaoming.qqbot.id.QqIdMapper
import cn.qfys521.xiaoming.qqbot.message.QqMessage
import net.mamoe.mirai.message.data.MessageChain
import net.mamoe.mirai.message.data.MessageChainBuilder
import net.mamoe.mirai.message.data.PlainText
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.Collections
import java.util.Optional

/**
 * QQ 官方机器人适配器针对小明用户（[XiaoMingUser]）接口的实现类。
 *
 * 表示 QQ 群聊中的发送者或单聊中的用户身份，并提供文本和命令消息的回传能力。
 *
 * @property bot 小明机器人内核实例
 * @property contact 当前用户归属的 QQ 会话（[QqContact]）
 * @property userId QQ 开放平台用户的 OpenID
 * @property userName 用户昵称或显示别称
 *
 * @author qfys521
 * @since 1.0.0
 */
class QqUser(
    private var bot: XiaoMingBot,
    private val contact: QqContact,
    val userId: String,
    private val userName: String = userId
) : XiaoMingUser<XiaoMingContact<*>> {

    private val logger: Logger = LoggerFactory.getLogger("QqUser-$userId")
    private val tags: MutableSet<String> = mutableSetOf("all")
    private val properties: MutableMap<PropertyType<*>, Any?> = mutableMapOf()

    private var receptionist: Receptionist? = null
    private var receptionTask: ReceptionTask<XiaoMingUser<XiaoMingContact<*>>>? = null
    private var interactorContext: InteractorContext? = null

    override fun getXiaoMingBot(): XiaoMingBot = bot

    override fun setXiaoMingBot(bot: XiaoMingBot) {
        this.bot = bot
    }

    override fun getLogger(): Logger = logger

    override fun getCode(): Long {
        return QqIdMapper.toLongId(userId)
    }

    override fun getCodeString(): String = userId

    override fun getCompleteName(): String = "QQ用户($userName - $userId)"

    override fun getContact(): XiaoMingContact<*> = contact

    override fun getReceptionist(): Receptionist? = receptionist

    override fun setReceptionist(receptionist: Receptionist?) {
        this.receptionist = receptionist
    }

    override fun getReceptionTask(): ReceptionTask<XiaoMingUser<XiaoMingContact<*>>>? = receptionTask

    override fun setReceptionTask(receptionTask: ReceptionTask<XiaoMingUser<XiaoMingContact<*>>>?) {
        this.receptionTask = receptionTask
    }

    override fun getInteractorContext(): InteractorContext? = interactorContext

    override fun setInteractorContext(interactorContext: InteractorContext?) {
        this.interactorContext = interactorContext
    }

    override fun sendMessage(message: String?, vararg arguments: Any?): Optional<Message> {
        if (message == null) return Optional.empty()
        val formatted = bot.languageManager.formatAdditional(message, { _ -> null }, *arguments)
        val chain = MessageChainBuilder().append(PlainText(formatted)).build()
        return contact.sendMessage(chain)
    }

    override fun sendMessage(messages: MessageChain?): Optional<Message> {
        return contact.sendMessage(messages)
    }

    override fun sendPrivateMessage(message: String?, vararg arguments: Any?): Optional<Message> {
        return sendMessage(message, *arguments)
    }

    override fun sendError(miraiCode: String?, vararg contexts: Any?): Optional<Message> {
        val msg = "ヾ(≧へ≦)〃 ${miraiCode ?: ""}"
        return sendMessage(msg, *contexts)
    }

    override fun nudge() {}

    override fun onNextMessage(messages: MessageChain?): Boolean {
        val text = messages?.contentToString() ?: return false
        val msg = QqMessage(bot, text, System.currentTimeMillis())
        return onNextMessage(msg)
    }

    override fun nextMessage(timeout: Long): Optional<Message> {
        return bot.contactManager.nextMessageEvent(timeout) { event ->
            event.user.contact.code == contact.code && event.user.code == this.code
        }.map { event ->
            val message = event.message
            val serializedMessage = message.serialize()
            if (serializedMessage == "退出") {
                throw cn.chuanwise.xiaoming.exception.InteractExitedException()
            } else {
                bot.statistician.increaseCallNumber()
                message
            }
        }
    }

    override fun getTags(): Set<String> = Collections.unmodifiableSet(tags)

    override fun getOriginalTags(): Set<String> = setOf("all")

    override fun flush() {}

    override fun addTag(tag: String): Boolean = tags.add(tag)

    override fun hasTag(tag: String): Boolean = tags.contains(tag)

    override fun removeTag(tag: String?): Boolean = tag != null && tags.remove(tag)

    @Suppress("UNCHECKED_CAST")
    override fun getProperties(): MutableMap<PropertyType<*>, Any> {
        return properties as MutableMap<PropertyType<*>, Any>
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T : Any?> getProperty(propertyType: PropertyType<T>?): Container<T> {
        if (propertyType == null) return Container.empty()
        val value = properties[propertyType] as? T
        return if (value == null) Container.empty() else Container.of(value)
    }

    override fun <T : Any?> setProperty(propertyType: PropertyType<T>?, value: T) {
        if (propertyType != null) {
            properties[propertyType] = value
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T : Any?> removeProperty(propertyType: PropertyType<T>?): Container<T> {
        if (propertyType == null) return Container.empty()
        val value = properties.remove(propertyType) as? T
        return if (value == null) Container.empty() else Container.of(value)
    }

    override fun <T : Any?> waitProperty(propertyType: PropertyType<T>?, timeout: Long): Container<T> {
        val deadline = System.currentTimeMillis() + timeout
        while (System.currentTimeMillis() < deadline) {
            val v = getProperty(propertyType)
            if (v.isPresent) return v
            Thread.sleep(100)
        }
        return Container.empty()
    }
}
