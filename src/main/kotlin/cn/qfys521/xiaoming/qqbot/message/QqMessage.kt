package cn.qfys521.xiaoming.qqbot.message

import cn.chuanwise.xiaoming.bot.XiaoMingBot
import cn.chuanwise.xiaoming.contact.message.Message
import net.mamoe.mirai.message.data.MessageChain
import net.mamoe.mirai.message.data.MessageChainBuilder
import net.mamoe.mirai.message.data.PlainText

/**
 * QQ 官方机器人适配器针对小明消息（[Message]）接口的实现类。
 *
 * 桥接来自 [cn.qfys521.qqbot.QQBot] 接收到的群聊或私信文本，并将其转换为
 * XiaoMingBot 内核以及各大交互器插件可识别的 Mirai [MessageChain]。
 *
 * @property bot 小明机器人内核实例
 * @property content QQ 消息的核心文本内容
 * @property time 消息接收或生成的时间戳（毫秒）
 * @property messageId 官方 QQ 网关分发的 `id` (msg_id)，用于发信回复和被动引用
 * @property rawEvent 原始事件对象（如 `GroupMessageEvent` 或 `C2CMessageEvent`）
 *
 * @author qfys521
 * @since 1.0.0
 */
class QqMessage(
    private var bot: XiaoMingBot,
    val content: String,
    private val time: Long,
    val messageId: String = "",
    val rawEvent: Any? = null
) : Message {

    private var currentChain: MessageChain = MessageChainBuilder()
        .append(PlainText(content))
        .build()

    private var originalChain: MessageChain = currentChain

    override fun getXiaoMingBot(): XiaoMingBot = bot

    override fun setXiaoMingBot(bot: XiaoMingBot) {
        this.bot = bot
    }

    override fun getMessageChain(): MessageChain = currentChain

    override fun setMessageChain(messageChain: MessageChain) {
        this.currentChain = messageChain
    }

    override fun getOriginalMessageChain(): MessageChain = originalChain

    override fun setOriginalMessageChain(messageChain: MessageChain) {
        this.originalChain = messageChain
    }

    override fun getTime(): Long = time

    override fun serialize(): String = content

    override fun serializeOriginalMessage(): String = content

    override fun getInternalMessageCode(): IntArray = IntArray(0)

    override fun getMessageCode(): IntArray = IntArray(0)

    override fun summary(): String {
        return if (content.length > 50) {
            "${content.substring(0, 47)}..."
        } else {
            content
        }
    }
}
