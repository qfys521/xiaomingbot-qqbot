package cn.qfys521.xiaoming.qqbot.console

import cn.chuanwise.xiaoming.bot.XiaoMingBot
import cn.chuanwise.xiaoming.contact.contact.ConsoleContact
import cn.chuanwise.xiaoming.contact.message.Message
import cn.qfys521.xiaoming.qqbot.message.QqMessage
import net.mamoe.mirai.contact.Friend
import net.mamoe.mirai.message.data.MessageChain
import net.mamoe.mirai.utils.ExternalResource
import net.mamoe.mirai.message.data.Image
import org.slf4j.LoggerFactory
import java.lang.reflect.Proxy
import java.util.Optional
import java.util.function.Predicate

/**
 * 控制台联系人实现，用于在后台控制台执行指令并接收输出。
 *
 * 实现 [ConsoleContact] 接口，通过 SLF4J 日志将小明框架的响应消息直接打印到控制台，
 * 替代原 Mirai `ConsoleContactImpl` 中对 `Bot.getAsFriend()` 的依赖。
 *
 * @property bot 小明机器人内核实例
 *
 * @author qfys521
 * @since 1.0.0
 */
class QqConsoleContact(
    private var bot: XiaoMingBot
) : ConsoleContact {

    private val logger = LoggerFactory.getLogger("Console")
    private val proxyFriend: Friend = createFakeConsoleFriend(bot.code)

    override fun getXiaoMingBot(): XiaoMingBot = bot
    override fun setXiaoMingBot(bot: XiaoMingBot) { this.bot = bot }

    override fun getMiraiContact(): Friend = proxyFriend

    override fun getCode(): Long = bot.code
    override fun getCodeString(): String = bot.code.toString()

    override fun sendMessage(messages: MessageChain?): Optional<Message> {
        val text = messages?.contentToString() ?: return Optional.empty()
        logger.info("[小明] {}", text)
        return Optional.of(QqMessage(bot, text, System.currentTimeMillis()))
    }

    override fun uploadImage(resource: ExternalResource?): Image {
        throw UnsupportedOperationException("控制台不支持上传图片")
    }

    override fun flush() {}
    override fun addTag(tag: String): Boolean = false
    override fun hasTag(tag: String): Boolean = tag == "all" || tag == "console"
    override fun removeTag(tag: String?): Boolean = false

    companion object {
        private fun createFakeConsoleFriend(id: Long): Friend {
            return Proxy.newProxyInstance(
                Friend::class.java.classLoader,
                arrayOf(Friend::class.java)
            ) { _, method, _ ->
                when (method.name) {
                    "getId" -> id
                    "getNick", "getName", "toString" -> "后台"
                    "getAvatarUrl" -> ""
                    else -> when (method.returnType) {
                        Boolean::class.java, java.lang.Boolean::class.java -> false
                        Long::class.java, Long::class.javaObjectType -> 0L
                        Int::class.java, Int::class.javaObjectType -> 0
                        String::class.java -> ""
                        else -> null
                    }
                }
            } as Friend
        }
    }
}
