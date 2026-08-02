package cn.qfys521.xiaoming.qqbot.console

import cn.chuanwise.toolkit.container.Container
import cn.chuanwise.xiaoming.bot.XiaoMingBot
import cn.chuanwise.xiaoming.contact.contact.ConsoleContact
import cn.chuanwise.xiaoming.contact.message.Message
import cn.chuanwise.xiaoming.interactor.context.InteractorContext
import cn.chuanwise.xiaoming.property.PropertyType
import cn.chuanwise.xiaoming.recept.ReceptionTask
import cn.chuanwise.xiaoming.recept.Receptionist
import cn.chuanwise.xiaoming.user.ConsoleXiaoMingUser
import cn.chuanwise.xiaoming.user.XiaoMingUser
import cn.qfys521.xiaoming.qqbot.message.QqMessage
import net.mamoe.mirai.message.data.MessageChain
import net.mamoe.mirai.message.data.MessageChainBuilder
import net.mamoe.mirai.message.data.PlainText
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.Optional

/**
 * 控制台小明用户实现，用于从 stdin 读取输入并模拟为小明用户发送指令。
 *
 * 该用户拥有最高权限（等同于 [ConsoleXiaoMingUser]），可以执行所有小明内核指令。
 *
 * @property consoleContact 控制台联系人实例
 *
 * @author qfys521
 * @since 1.0.0
 */
class QqConsoleUser(
    private var bot: XiaoMingBot,
    private val consoleContact: QqConsoleContact
) : ConsoleXiaoMingUser {

    private val userLogger: Logger = LoggerFactory.getLogger("ConsoleUser")
    private val tags: MutableSet<String> = mutableSetOf("all", "console")
    private val properties: MutableMap<PropertyType<*>, Any?> = mutableMapOf()

    private var receptionist: Receptionist? = null
    @Suppress("UNCHECKED_CAST")
    private var receptionTask: ReceptionTask<XiaoMingUser<ConsoleContact>>? = null
    private var interactorContext: InteractorContext? = null

    override fun getXiaoMingBot(): XiaoMingBot = bot
    override fun setXiaoMingBot(bot: XiaoMingBot) { this.bot = bot }

    override fun getLogger(): Logger = userLogger

    override fun getCode(): Long = bot.code
    override fun getCodeString(): String = bot.code.toString()

    override fun getCompleteName(): String = "后台"

    override fun getContact(): ConsoleContact = consoleContact

    override fun getReceptionist(): Receptionist? = receptionist
    override fun setReceptionist(receptionist: Receptionist?) { this.receptionist = receptionist }

    override fun getReceptionTask(): ReceptionTask<XiaoMingUser<ConsoleContact>>? = receptionTask

    @Suppress("UNCHECKED_CAST")
    override fun setReceptionTask(task: ReceptionTask<XiaoMingUser<ConsoleContact>>?) {
        this.receptionTask = task
    }

    override fun getInteractorContext(): InteractorContext? = interactorContext
    override fun setInteractorContext(ctx: InteractorContext?) { this.interactorContext = ctx }

    override fun sendMessage(message: String?, vararg arguments: Any?): Optional<Message> {
        if (message == null) return Optional.empty()
        val formatted = bot.languageManager.formatAdditional(message, { _ -> null }, *arguments)
        val chain = MessageChainBuilder().append(PlainText(formatted)).build()
        return consoleContact.sendMessage(chain)
    }

    override fun sendMessage(messages: MessageChain?): Optional<Message> {
        return consoleContact.sendMessage(messages)
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

    override fun getTags(): Set<String> = tags
    override fun getOriginalTags(): Set<String> = setOf("all", "console")
    override fun flush() {}
    override fun addTag(tag: String): Boolean = tags.add(tag)
    override fun hasTag(tag: String): Boolean = tags.contains(tag)
    override fun removeTag(tag: String?): Boolean = tag != null && tags.remove(tag)

    @Suppress("UNCHECKED_CAST")
    override fun getProperties(): MutableMap<PropertyType<*>, Any> {
        return properties as MutableMap<PropertyType<*>, Any>
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T> getProperty(type: PropertyType<T>?): Container<T> {
        if (type == null) return Container.empty()
        val v = properties[type] as? T
        return if (v == null) Container.empty() else Container.of(v)
    }

    override fun <T> setProperty(type: PropertyType<T>?, value: T) {
        if (type != null) properties[type] = value
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T> removeProperty(type: PropertyType<T>?): Container<T> {
        if (type == null) return Container.empty()
        val v = properties.remove(type) as? T
        return if (v == null) Container.empty() else Container.of(v)
    }

    override fun <T> waitProperty(type: PropertyType<T>?, timeout: Long): Container<T> {
        val deadline = System.currentTimeMillis() + timeout
        while (System.currentTimeMillis() < deadline) {
            val v = getProperty(type)
            if (v.isPresent) return v
            Thread.sleep(100)
        }
        return Container.empty()
    }
}
