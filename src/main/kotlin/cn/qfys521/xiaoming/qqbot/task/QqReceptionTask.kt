package cn.qfys521.xiaoming.qqbot.task

import cn.chuanwise.xiaoming.contact.message.Message
import cn.chuanwise.xiaoming.recept.ReceptionTaskImpl
import cn.chuanwise.xiaoming.user.XiaoMingUser

/**
 * QQ 官方机器人接待任务（[ReceptionTaskImpl]）扩展实现类。
 *
 * 用于将接收到的 QQ 官方网关消息包装为一个接待并发任务，提交给 [cn.chuanwise.xiaoming.schedule.Scheduler] 异步调度调度执行。
 *
 * @param user 提交当前请求的消息发送者（[XiaoMingUser]）
 * @param message 将被解析执行的消息体对象（[Message]）
 *
 * @author qfys521
 * @since 1.0.0
 */
class QqReceptionTask(
    user: XiaoMingUser<*>,
    message: Message
) : ReceptionTaskImpl<XiaoMingUser<*>>(user, message)
