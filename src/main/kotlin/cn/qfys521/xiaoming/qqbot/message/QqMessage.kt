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

    private var currentChain: MessageChain = MessageChainBuilder().apply {
        val regex = "<@!?([A-Za-z0-9_]+)>".toRegex()
        val hexRegex = "\\b[A-Fa-f0-9]{32}\\b".toRegex()
        
        fun processPlainText(text: String): String {
            return hexRegex.replace(text) { match ->
                cn.qfys521.xiaoming.qqbot.id.QqIdMapper.toLongId(match.value).toString()
            }
        }
        
        var lastMatchEnd = 0
        
        regex.findAll(content).forEach { matchResult ->
            val textBefore = content.substring(lastMatchEnd, matchResult.range.first)
            if (textBefore.isNotEmpty()) {
                append(PlainText(processPlainText(textBefore)))
            }
            
            val openId = matchResult.groupValues[1]
            val longId = cn.qfys521.xiaoming.qqbot.id.QqIdMapper.toLongId(openId)
            append(net.mamoe.mirai.message.data.At(longId))
            
            lastMatchEnd = matchResult.range.last + 1
        }
        
        val textAfter = content.substring(lastMatchEnd)
        if (textAfter.isNotEmpty()) {
            append(PlainText(processPlainText(textAfter)))
        }
        
        if (rawEvent is cn.qfys521.qqbot.event.BotEvent) {
            val messageObj = when (rawEvent) {
                is cn.qfys521.qqbot.event.GroupMessageEvent -> rawEvent.message
                is cn.qfys521.qqbot.event.GroupAtMessageEvent -> rawEvent.message
                is cn.qfys521.qqbot.event.C2CMessageEvent -> rawEvent.message
                is cn.qfys521.qqbot.event.GuildMessageEvent -> rawEvent.message
                is cn.qfys521.qqbot.event.GuildAtMessageEvent -> rawEvent.message
                is cn.qfys521.qqbot.event.DirectMessageEvent -> rawEvent.message
                else -> null
            }
            
            messageObj?.attachments?.forEach { attachment ->
                val type = attachment.contentType?.lowercase() ?: ""
                when {
                    type.startsWith("image/") -> append(PlainText(" [图片]"))
                    type.startsWith("audio/") || type.startsWith("voice/") -> append(PlainText(" [语音]"))
                    type.startsWith("video/") -> append(PlainText(" [视频]"))
                    else -> append(PlainText(" [文件]"))
                }
            }
        }
    }.build()

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

    override fun serialize(): String = currentChain.serializeToMiraiCode()

    override fun serializeOriginalMessage(): String = originalChain.serializeToMiraiCode()

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
