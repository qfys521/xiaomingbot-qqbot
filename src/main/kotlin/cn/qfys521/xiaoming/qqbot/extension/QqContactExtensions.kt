package cn.qfys521.xiaoming.qqbot.extension

import cn.chuanwise.xiaoming.contact.contact.XiaoMingContact
import cn.qfys521.qqbot.model.message.Keyboard
import cn.qfys521.qqbot.model.message.MediaInfo
import cn.qfys521.qqbot.model.message.MessageMarkdown
import cn.qfys521.qqbot.model.message.MessageResult
import cn.qfys521.qqbot.model.message.SendMessageRequest
import cn.qfys521.qqbot.model.message.UploadMediaRequest
import cn.qfys521.xiaoming.qqbot.contact.QqContact

/**
 * 针对 [QqContact] 的高级扩展函数库。
 *
 * 为小明开发者在编写交互指令或插件时，提供一键发信函数支持：
 * - 发送原生或模板 Markdown 消息 (`sendMarkdown`)
 * - 发送卡片互动键盘按钮 (`sendKeyboard`)
 * - 发送图片、视频、语音富媒体消息 (`sendImage` / `sendVideo` / `sendAudio`)
 *
 * @author qfys521
 * @since 1.0.0
 */

/**
 * 尝试将通用的小明会话接口转换为 [QqContact]。
 */
fun XiaoMingContact<*>.asQqContact(): QqContact? = this as? QqContact

/**
 * 发送原生 Markdown 渲染格式的文本内容。
 *
 * @param markdownContent 原生 Markdown 字符串内容
 * @param msgId 关联回复的目标事件或消息 ID，默认为会话最新的一条 [QqContact.lastMessageId]
 * @return 官方回包收据 [MessageResult]
 */
suspend fun QqContact.sendMarkdown(
    markdownContent: String,
    msgId: String? = this.lastMessageId.ifEmpty { null }
): MessageResult {
    val req = SendMessageRequest(
        msgType = 2,
        markdown = MessageMarkdown(content = markdownContent),
        msgId = msgId
    )
    return if (isDirect) {
        qqBot.api.sendC2CMessage(contactId, req)
    } else {
        qqBot.api.sendGroupMessage(contactId, req)
    }
}

/**
 * 发送基于平台模板 ID 的 Markdown 消息。
 *
 * @param templateId 开放平台标准模板 ID
 * @param msgId 回复目标消息 ID
 */
suspend fun QqContact.sendMarkdownTemplate(
    templateId: Int,
    msgId: String? = this.lastMessageId.ifEmpty { null }
): MessageResult {
    val req = SendMessageRequest(
        msgType = 2,
        markdown = MessageMarkdown(templateId = templateId),
        msgId = msgId
    )
    return if (isDirect) {
        qqBot.api.sendC2CMessage(contactId, req)
    } else {
        qqBot.api.sendGroupMessage(contactId, req)
    }
}

/**
 * 发送基于开发者在平台配置的自定义模板 ID 的 Markdown 消息。
 *
 * @param customTemplateId 后台配置的自定义模板唯一字串
 * @param msgId 回复目标消息 ID
 */
suspend fun QqContact.sendMarkdownCustom(
    customTemplateId: String,
    msgId: String? = this.lastMessageId.ifEmpty { null }
): MessageResult {
    val req = SendMessageRequest(
        msgType = 2,
        markdown = MessageMarkdown(customTemplateId = customTemplateId),
        msgId = msgId
    )
    return if (isDirect) {
        qqBot.api.sendC2CMessage(contactId, req)
    } else {
        qqBot.api.sendGroupMessage(contactId, req)
    }
}

/**
 * 发送附带交互键盘 [Keyboard]（按钮菜单）的消息。
 *
 * @param keyboard 构造好的交互键盘或按钮定义
 * @param content 伴随展示的引导文本提示，默认为 "请选择："
 * @param msgId 回复目标消息 ID
 */
suspend fun QqContact.sendKeyboard(
    keyboard: Keyboard,
    content: String = "请选择：",
    msgId: String? = this.lastMessageId.ifEmpty { null }
): MessageResult {
    val req = SendMessageRequest(
        msgType = 0,
        content = content,
        keyboard = keyboard,
        msgId = msgId
    )
    return if (isDirect) {
        qqBot.api.sendC2CMessage(contactId, req)
    } else {
        qqBot.api.sendGroupMessage(contactId, req)
    }
}

/**
 * 上传并向 QQ 群或 C2C 单聊快速下发图片消息。
 *
 * 底层利用官方 `/v2/groups/{id}/files` 或 `/v2/users/{id}/files` 转存接口。
 *
 * @param imageUrl 公网可访问的原始图片网络链接（支持 jpg/png）
 * @param directSend 是否设置 `srv_send_msg = true` 让平台在上传完成的一瞬间立刻直发（默认 `true`，更快更稳定）
 * @param msgId 若 `directSend = false`，则随后组装 `msg_type = 7` 回信时关联的消息 ID
 * @return 官方发信结果记录
 */
suspend fun QqContact.sendImage(
    imageUrl: String,
    directSend: Boolean = true,
    msgId: String? = this.lastMessageId.ifEmpty { null }
): MessageResult {
    return sendMediaInternal(fileType = 1, url = imageUrl, directSend = directSend, msgId = msgId)
}

/**
 * 上传并下发网络视频消息 (`fileType = 2`, 支持 mp4)。
 */
suspend fun QqContact.sendVideo(
    videoUrl: String,
    directSend: Boolean = true,
    msgId: String? = this.lastMessageId.ifEmpty { null }
): MessageResult {
    return sendMediaInternal(fileType = 2, url = videoUrl, directSend = directSend, msgId = msgId)
}

/**
 * 上传并下发语音消息 (`fileType = 3`, 支持 silk 语音格式)。
 */
suspend fun QqContact.sendAudio(
    audioUrl: String,
    directSend: Boolean = true,
    msgId: String? = this.lastMessageId.ifEmpty { null }
): MessageResult {
    return sendMediaInternal(fileType = 3, url = audioUrl, directSend = directSend, msgId = msgId)
}

private suspend fun QqContact.sendMediaInternal(
    fileType: Int,
    url: String,
    directSend: Boolean,
    msgId: String?
): MessageResult {
    val uploadReq = UploadMediaRequest(
        fileType = fileType,
        url = url,
        srvSendMsg = if (directSend) true else null
    )
    val uploadRes = if (isDirect) {
        qqBot.api.uploadC2CMedia(contactId, uploadReq)
    } else {
        qqBot.api.uploadGroupMedia(contactId, uploadReq)
    }

    // 若配置了直发并且得到了应答 ID，立即以发件结果回抛
    if (directSend && !uploadRes.id.isNullOrEmpty()) {
        return MessageResult(id = uploadRes.id)
    }

    // 否则取凭证手动调用 msg_type = 7 下发
    val fileInfoToken = uploadRes.fileInfo
        ?: throw IllegalStateException("QQ 官方网关返回空媒体 fileInfo 凭证，无法发送媒体文件: URL=$url")

    val req = SendMessageRequest(
        msgType = 7,
        media = MediaInfo(fileInfo = fileInfoToken),
        msgId = msgId
    )
    return if (isDirect) {
        qqBot.api.sendC2CMessage(contactId, req)
    } else {
        qqBot.api.sendGroupMessage(contactId, req)
    }
}
