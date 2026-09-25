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

    private val sourceMessage: cn.qfys521.qqbot.model.message.Message? = extractMessage(rawEvent)

    private var currentChain: MessageChain = MessageChainBuilder().apply {
        val explicitMentionRegex = "<@!?([A-Za-z0-9_]+)>".toRegex()
        val hexRegex = "\\b[A-Fa-f0-9]{32}\\b".toRegex()

        fun processPlainText(text: String): String {
            return hexRegex.replace(text) { match ->
                cn.qfys521.xiaoming.qqbot.id.QqIdMapper.toLongId(match.value).toString()
            }
        }

        fun appendPlainText(text: String) {
            if (text.isNotEmpty()) {
                append(PlainText(processPlainText(text)))
            }
        }

        val sourceMentions = sourceMessage?.mentions
            .orEmpty()
            .filter { it.openId.isNotBlank() }

        val namedMentions = buildList {
            var searchStart = 0
            sourceMentions
                .filter { it.username.isNotBlank() }
                .forEach { user ->
                    val marker = "@${user.username}"
                    val index = content.indexOf(marker, searchStart)
                    if (index >= 0) {
                        add(NamedMention(index, index + marker.length, user))
                        searchStart = index + marker.length
                    }
                }
        }

        var lastMatchEnd = 0
        val explicitMentions = explicitMentionRegex.findAll(content).toList()
        val allMentionRanges = buildList {
            explicitMentions.forEach { matchResult ->
                add(
                    MentionRange(
                        start = matchResult.range.first,
                        endExclusive = matchResult.range.last + 1,
                        openId = matchResult.groupValues[1]
                    )
                )
            }
            namedMentions.forEach { mention ->
                add(
                    MentionRange(
                        start = mention.start,
                        endExclusive = mention.endExclusive,
                        openId = mention.user.openId
                    )
                )
            }
        }.sortedWith(compareBy<MentionRange> { it.start }.thenByDescending { it.endExclusive })

        var lastMentionEnd = -1
        allMentionRanges.forEach { mention ->
            // Do not let overlapping named/explicit mention ranges produce two At elements.
            if (mention.start < lastMentionEnd) return@forEach

            appendPlainText(content.substring(lastMatchEnd, mention.start))
            append(net.mamoe.mirai.message.data.At(
                cn.qfys521.xiaoming.qqbot.id.QqIdMapper.toLongId(mention.openId)
            ))
            lastMatchEnd = mention.endExclusive
            lastMentionEnd = mention.endExclusive
        }

        if (allMentionRanges.isNotEmpty()) {
            appendPlainText(content.substring(lastMatchEnd))
        } else if (sourceMentions.isNotEmpty()) {
            // QQ sometimes supplies the real mentioned users in `mentions`, but
            // the text contains only their displayed names (or a localized/empty
            // display token). In that case, bind @-tokens to the mention objects
            // by order so XiaoMing receives Mirai At elements instead of text.
            val fallbackRanges = content
                .mapAtTokenRanges()
                .take(sourceMentions.size)
                .mapIndexed { index, range ->
                    MentionRange(range.first, range.last + 1, sourceMentions[index].openId)
                }

            var fallbackLastEnd = 0
            fallbackRanges.forEach { mention ->
                appendPlainText(content.substring(fallbackLastEnd, mention.start))
                append(net.mamoe.mirai.message.data.At(
                    cn.qfys521.xiaoming.qqbot.id.QqIdMapper.toLongId(mention.openId)
                ))
                fallbackLastEnd = mention.endExclusive
            }
            appendPlainText(content.substring(fallbackLastEnd))
        } else {
            appendPlainText(content)
        }

        sourceMessage?.attachments.orEmpty().forEach { attachment ->
            val type = attachment.contentType?.lowercase() ?: ""
            when {
                type.startsWith("image/") -> append(PlainText(" [\u56fe\u7247]"))
                type.startsWith("audio/") || type.startsWith("voice/") -> append(PlainText(" [\u8bed\u97f3]"))
                type.startsWith("video/") -> append(PlainText(" [\u89c6\u9891]"))
                else -> append(PlainText(" [\u6587\u4ef6]"))
            }
        }
    }.build()

    private data class MentionRange(
        val start: Int,
        val endExclusive: Int,
        val openId: String
    )

    private data class NamedMention(
        val start: Int,
        val endExclusive: Int,
        val user: cn.qfys521.qqbot.model.user.User
    )

    private fun String.mapAtTokenRanges(): List<IntRange> {
        val ranges = mutableListOf<IntRange>()
        var index = 0
        while (index < length) {
            if (this[index] != '@' || (index > 0 && this[index - 1] == '<')) {
                index++
                continue
            }

            var end = index + 1
            while (end < length && !this[end].isWhitespace()) end++
            if (end > index + 1) {
                ranges += index until end
                index = end
            } else {
                index++
            }
        }
        return ranges
    }

    private fun extractMessage(event: Any?): cn.qfys521.qqbot.model.message.Message? {
        return when (event) {
            is cn.qfys521.qqbot.event.GroupMessageEvent -> event.message
            is cn.qfys521.qqbot.event.GroupAtMessageEvent -> event.message
            is cn.qfys521.qqbot.event.C2CMessageEvent -> event.message
            is cn.qfys521.qqbot.event.GuildMessageEvent -> event.message
            is cn.qfys521.qqbot.event.GuildAtMessageEvent -> event.message
            is cn.qfys521.qqbot.event.DirectMessageEvent -> event.message
            else -> null
        }
    }

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
