package cn.qfys521.xiaoming.qqbot.config

import cn.qfys521.qqbot.model.common.Intent
import kotlinx.serialization.Serializable

/**
 * QQ 官方机器人接入小明框架（XiaoMingBot）的配置类。
 *
 * 用于定义小明机器人接入 QQ 开放平台所需身份验证数据、运行环境及事件订阅选项。
 *
 * ## intents 配置说明
 *
 * `intents` 字段为一个字符串列表，每项对应一个事件订阅名称。启动时框架会自动将其
 * 转换为 QQ 官方网关所需的位掩码（bitmask）。
 *
 * ### 可用的 Intent 名称
 *
 * | 名称                       | 说明                                         | 适用场景         |
 * |----------------------------|----------------------------------------------|------------------|
 * | `GROUP_AND_C2C_EVENT`      | 群聊 @机器人 消息 + C2C 单聊消息              | ✅ 群聊/私聊机器人 |
 * | `GUILDS`                   | 频道创建、更新、删除等管理事件                | 频道机器人       |
 * | `GUILD_MEMBERS`            | 频道成员加入/退出事件                         | 频道机器人       |
 * | `GUILD_MESSAGES`           | 频道内全部消息（需私域权限）                  | 私域频道机器人   |
 * | `GUILD_AT_MESSAGES`        | 频道内 @机器人 消息                           | 公域频道机器人   |
 * | `INTERACTION`              | 内嵌按钮/菜单交互回调事件                     | 按钮交互场景     |
 *
 * ### 配置示例
 *
 * 仅接收群聊和私聊消息（推荐大多数标准 QQ 群聊机器人使用）：
 * ```json
 * "intents": ["GROUP_AND_C2C_EVENT"]
 * ```
 *
 * 同时接收群聊私聊消息和按钮交互事件：
 * ```json
 * "intents": ["GROUP_AND_C2C_EVENT", "INTERACTION"]
 * ```
 *
 * 频道机器人订阅全部常规事件：
 * ```json
 * "intents": ["GUILDS", "GUILD_MEMBERS", "GUILD_AT_MESSAGES", "INTERACTION", "GROUP_AND_C2C_EVENT"]
 * ```
 *
 * @property appId QQ 机器人的 AppID
 * @property clientSecret QQ 机器人的 ClientSecret
 * @property token QQ 机器人的 Bot Token
 * @property sandbox 是否为沙盒环境（通常应为 `false` 以连接生产网关）
 * @property shardId 分片连接当前的 Shard Index（默认 `0`）
 * @property shardCount 分片连接总计 Shard Count（默认 `1`）
 * @property intents 订阅的事件名称列表，默认仅开启 `GROUP_AND_C2C_EVENT`
 * @property workingDirectory 工作目录路径，默认为当前目录
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
    val intents: List<String> = listOf("GROUP_AND_C2C_EVENT"),
    val workingDirectory: String = "."
) {
    /**
     * 校验配置参数完整性，若缺少必要参数将抛出 [IllegalArgumentException]。
     */
    fun validate() {
        require(appId.isNotBlank()) { "QqBotConfig 错误: appId 不能为空！" }
        require(clientSecret.isNotBlank()) { "QqBotConfig 错误: clientSecret 不能为空！" }
        require(token.isNotBlank()) { "QqBotConfig 错误: token 不能为空！" }
        require(intents.isNotEmpty()) { "QqBotConfig 错误: intents 不能为空列表，至少需要订阅一个事件！" }
    }

    companion object {
        /**
         * Intent 名称到位掩码的映射表。
         */
        private val INTENT_MAP: Map<String, Int> = mapOf(
            "GUILDS" to Intent.GUILDS,
            "GUILD_MEMBERS" to Intent.GUILD_MEMBERS,
            "GUILD_MESSAGES" to Intent.GUILD_MESSAGES,
            "GUILD_AT_MESSAGES" to Intent.GUILD_AT_MESSAGES,
            "PUBLIC_GUILD_MESSAGES" to Intent.PUBLIC_GUILD_MESSAGES,
            "INTERACTION" to Intent.INTERACTION,
            "GROUP_AND_C2C_EVENT" to Intent.GROUP_AND_C2C_EVENT
        )

        /**
         * 将 Intent 名称列表解析为按位或组合后的位掩码整数值。
         *
         * @param intentNames Intent 名称列表
         * @return 组合后的位掩码值
         * @throws IllegalArgumentException 如果包含无法识别的 Intent 名称
         */
        fun resolveIntents(intentNames: List<String>): Int {
            var result = 0
            for (name in intentNames) {
                val trimmed = name.trim().uppercase()
                val value = INTENT_MAP[trimmed]
                    ?: throw IllegalArgumentException(
                        "无法识别的 Intent 名称: \"$name\"。可用的名称有: ${INTENT_MAP.keys.joinToString(", ")}"
                    )
                result = result or value
            }
            return result
        }
    }
}
