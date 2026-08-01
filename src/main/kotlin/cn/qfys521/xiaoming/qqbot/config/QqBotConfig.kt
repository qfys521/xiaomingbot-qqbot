package cn.qfys521.xiaoming.qqbot.config

import cn.qfys521.qqbot.model.common.Intent
import kotlinx.serialization.Serializable

/**
 * QQ 官方机器人接入小明框架（XiaoMingBot）的配置类。
 *
 * 用于定义小明机器人接入 QQ 开放平台所需身份验证数据、运行环境及事件订阅选项。
 *
 * @property appId QQ 机器人的 AppID
 * @property clientSecret QQ 机器人的 ClientSecret
 * @property token QQ 机器人的 Bot Token
 * @property sandbox 是否为沙盒环境（通常应为 `false` 以连接生产网关）
 * @property shardId 分片连接当前的 Shard Index（默认 `0`）
 * @property shardCount 分片连接总计 Shard Count（默认 `1`）
 * @property intents 订阅的网关 Intent 位掩码组合，默认开启 GROUP_AND_C2C_EVENT 等常规消息事件
 * @property workingDirectory 工作目录路径，默认为 `./xiaoming`
 *
 * @author qfys521
 * @since 1.0.0
 */
@Serializable
data class QqBotConfig(
    val appId: String = "",
    val clientSecret: String = "",
    val token: String = "",
    val sandbox: Boolean = false,
    val shardId: Int = 0,
    val shardCount: Int = 1,
    val intents: Int = Intent.GROUP_AND_C2C_EVENT or Intent.PUBLIC_GUILD_MESSAGES,
    val workingDirectory: String = "."
) {
    /**
     * 校验配置参数完整性，若缺少必要参数将抛出 [IllegalArgumentException]。
     */
    fun validate() {
        require(appId.isNotBlank()) { "QqBotConfig 错误: appId 不能为空！" }
        require(clientSecret.isNotBlank()) { "QqBotConfig 错误: clientSecret 不能为空！" }
        require(token.isNotBlank()) { "QqBotConfig 错误: token 不能为空！" }
    }
}
