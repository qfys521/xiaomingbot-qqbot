@file:Suppress("DuplicatedCode")

package cn.qfys521.xiaoming.qqbot.listener

import cn.chuanwise.xiaoming.event.MessageEvent
import cn.qfys521.qqbot.QQBot
import cn.qfys521.qqbot.event.C2CMessageEvent
import cn.qfys521.qqbot.event.DirectMessageEvent
import cn.qfys521.qqbot.event.GroupAtMessageEvent
import cn.qfys521.qqbot.event.GuildAtMessageEvent
import cn.qfys521.qqbot.event.GuildMessageEvent
import cn.qfys521.xiaoming.qqbot.QqBotImpl
import cn.qfys521.xiaoming.qqbot.contact.QqContact
import cn.qfys521.xiaoming.qqbot.message.QqMessage
import cn.qfys521.xiaoming.qqbot.task.QqReceptionTask
import cn.qfys521.xiaoming.qqbot.user.QqUser
import org.slf4j.LoggerFactory

/**
 * QQ 官方机器人事件桥接监听器。
 *
 * 将官方 QQBot Kotlin SDK 分发的事件解包转换，输入并绑定到 XiaoMingBot 内核的
 * [cn.chuanwise.xiaoming.contact.ContactManager] 与 [cn.chuanwise.xiaoming.schedule.Scheduler] 系统中。
 *
 * @property bot QQ 小明机器人核心包装对象
 * @property qqBot 官方 QQBot Kotlin SDK 实例
 *
 * @author qfys521
 * @since 1.0.0
 */
class QqEventListener(
    private val bot: QqBotImpl,
    private val qqBot: QQBot
) {
    private val logger = LoggerFactory.getLogger(QqEventListener::class.java)

    /**
     * 将事件接收分发注册至给定的 [QQBot] 实例。
     */
    fun registerToBot() {
        qqBot.eventDispatcher.onGroupAtMessage { event -> handleGroupMessage(event) }
        qqBot.eventDispatcher.onGroupMessage { event -> handleGroupMessage(event) }
        qqBot.eventDispatcher.onC2CMessage { event -> handleC2CMessage(event) }
        qqBot.eventDispatcher.onGuildAtMessage { event -> handleGuildAtMessage(event) }
        qqBot.eventDispatcher.onGuildMessage { event -> handleGuildMessage(event) }
        qqBot.eventDispatcher.onDirectMessage { event -> handleDirectMessage(event) }
    }

    private fun handleGroupMessage(event: cn.qfys521.qqbot.event.GroupMessageEvent) {
        try {
            val content = event.message.content.trim()
            if (content.isEmpty()) return

            val contactId = event.groupOpenId.ifEmpty { event.message.groupOpenId ?: "" }
            val contact = QqContact(
                bot = bot,
                qqBot = qqBot,
                contactId = contactId,
                contactName = "群-$contactId",
                isDirect = false,
                lastMessageId = event.message.id
            )
            val user = QqUser(
                bot = bot,
                contact = contact,
                userId = event.authorId,
                userName = event.authorName
            )
            val msg = QqMessage(
                bot = bot,
                content = content,
                time = System.currentTimeMillis(),
                messageId = event.message.id,
                rawEvent = event
            )

            dispatchToXiaoMing(user, msg)
        } catch (e: Exception) {
            logger.error("处理 QQ 普通群聊事件消息时发生错误: ${e.message}", e)
        }
    }

    private fun handleGroupMessage(event: GroupAtMessageEvent) {
        try {
            val content = event.message.content.trim()
            if (content.isEmpty()) return

            val contactId = event.groupOpenId.ifEmpty { event.message.groupOpenId ?: "" }
            val contact = QqContact(
                bot = bot,
                qqBot = qqBot,
                contactId = contactId,
                contactName = "群-$contactId",
                isDirect = false,
                lastMessageId = event.message.id
            )
            val user = QqUser(
                bot = bot,
                contact = contact,
                userId = event.authorId,
                userName = event.authorName
            )
            val msg = QqMessage(
                bot = bot,
                content = content,
                time = System.currentTimeMillis(),
                messageId = event.message.id,
                rawEvent = event
            )

            dispatchToXiaoMing(user, msg)
        } catch (e: Exception) {
            logger.error("处理 QQ 群组事件消息时发生错误: ${e.message}", e)
        }
    }

    private fun handleC2CMessage(event: C2CMessageEvent) {
        try {
            val content = event.message.content.trim()
            if (content.isEmpty()) return

            val contactId = event.userOpenId.ifEmpty { event.message.userOpenId ?: event.authorId }
            val contact = QqContact(
                bot = bot,
                qqBot = qqBot,
                contactId = contactId,
                contactName = event.authorName,
                isDirect = true,
                lastMessageId = event.message.id
            )
            val user = QqUser(
                bot = bot,
                contact = contact,
                userId = event.authorId,
                userName = event.authorName
            )
            val msg = QqMessage(
                bot = bot,
                content = content,
                time = System.currentTimeMillis(),
                messageId = event.message.id,
                rawEvent = event
            )

            dispatchToXiaoMing(user, msg)
        } catch (e: Exception) {
            logger.error("处理 QQ 单聊事件消息时发生错误: ${e.message}", e)
        }
    }

    private fun handleGuildAtMessage(event: GuildAtMessageEvent) {
        try {
            val content = event.message.content.trim()
            if (content.isEmpty()) return

            val contactId = event.message.channelId ?: ""
            val authorId = event.message.author.openId
            val authorName = event.message.author.username
            val contact = QqContact(
                bot = bot,
                qqBot = qqBot,
                contactId = contactId,
                contactName = "频道-$contactId",
                isDirect = false,
                lastMessageId = event.message.id
            )
            val user = QqUser(
                bot = bot,
                contact = contact,
                userId = authorId,
                userName = authorName
            )
            val msg = QqMessage(
                bot = bot,
                content = content,
                time = System.currentTimeMillis(),
                messageId = event.message.id,
                rawEvent = event
            )

            dispatchToXiaoMing(user, msg)
        } catch (e: Exception) {
            logger.error("处理 QQ 频道 @ 事件时发生错误: ${e.message}", e)
        }
    }

    private fun handleGuildMessage(event: GuildMessageEvent) {
        try {
            val content = event.message.content.trim()
            if (content.isEmpty()) return

            val contactId = event.message.channelId ?: ""
            val authorId = event.message.author.openId
            val authorName = event.message.author.username
            val contact = QqContact(
                bot = bot,
                qqBot = qqBot,
                contactId = contactId,
                contactName = "频道-$contactId",
                isDirect = false,
                lastMessageId = event.message.id
            )
            val user = QqUser(
                bot = bot,
                contact = contact,
                userId = authorId,
                userName = authorName
            )
            val msg = QqMessage(
                bot = bot,
                content = content,
                time = System.currentTimeMillis(),
                messageId = event.message.id,
                rawEvent = event
            )

            dispatchToXiaoMing(user, msg)
        } catch (e: Exception) {
            logger.error("处理 QQ 频道普通事件时发生错误: ${e.message}", e)
        }
    }

    private fun handleDirectMessage(event: DirectMessageEvent) {
        try {
            val content = event.message.content.trim()
            if (content.isEmpty()) return

            val contactId = event.message.guildId ?: ""
            val authorId = event.message.author.openId
            val authorName = event.message.author.username
            val contact = QqContact(
                bot = bot,
                qqBot = qqBot,
                contactId = contactId,
                contactName = authorName,
                isDirect = true,
                lastMessageId = event.message.id
            )
            val user = QqUser(
                bot = bot,
                contact = contact,
                userId = authorId,
                userName = authorName
            )
            val msg = QqMessage(
                bot = bot,
                content = content,
                time = System.currentTimeMillis(),
                messageId = event.message.id,
                rawEvent = event
            )

            dispatchToXiaoMing(user, msg)
        } catch (e: Exception) {
            logger.error("处理 QQ 频道私信事件时发生错误: ${e.message}", e)
        }
    }

    private fun dispatchToXiaoMing(user: QqUser, msg: QqMessage) {
        // 控制台输出收到的消息
        logger.info("[收到消息] {} ({}) -> {}", user.completeName, user.codeString, msg.serialize())

        bot.contactManager.onNextMessageEvent(MessageEvent(user, msg))
        bot.statistician.increaseCallNumber()
        bot.scheduler.run(QqReceptionTask(user, msg))
    }
}
