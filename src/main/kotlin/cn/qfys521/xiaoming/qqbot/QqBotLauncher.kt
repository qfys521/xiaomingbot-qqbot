package cn.qfys521.xiaoming.qqbot

import cn.chuanwise.xiaoming.event.MessageEvent
import cn.qfys521.xiaoming.qqbot.config.QqBotConfig
import cn.qfys521.xiaoming.qqbot.message.QqMessage
import cn.qfys521.xiaoming.qqbot.task.QqReceptionTask
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import kotlin.system.exitProcess

/**
 * QQ 官方机器人小明（XiaoMingBot）通用启动器命令行主程序。
 *
 * 启动时默认尝试从工作目录的 `qqbot.json` 加载鉴权配置，若文件不存在，则会自动生成
 * 一份包含完整字段说明的模板配置文件并提示用户填写。
 *
 * 支持控制台交互：启动后可在 stdin 中直接输入小明指令（如 `#help`），
 * 控制台用户拥有后台最高权限。
 *
 * @author qfys521
 * @since 1.0.0
 */
object QqBotLauncher {

    private val logger = LoggerFactory.getLogger(QqBotLauncher::class.java)
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /**
     * 应用程序入口主方法。
     *
     * @param args 命令行参数。如果提供非空数组，则第一个参数视为自定义的 json 配置文件路径。
     */
    @JvmStatic
    fun main(args: Array<String>) {
        val configPath = if (args.isNotEmpty()) args[0] else "qqbot.json"
        val configFile = File(configPath)

        if (!configFile.exists()) {
            logger.info("未检测到已存在的配置文件 [$configPath]，正在自动为您生成默认模板...")
            val defaultConfig = QqBotConfig(
                appId = "请输入您的 QQ 机器人 AppID",
                clientSecret = "请输入您的 QQ 机器人 ClientSecret",
                token = "请输入您的 QQ 机器人 Bot Token"
            )
            configFile.parentFile?.mkdirs()
            configFile.writeText(json.encodeToString(QqBotConfig.serializer(), defaultConfig), Charsets.UTF_8)
            logger.warn("=== 默认配置文件已生成在: ${configFile.absolutePath} ===")
            logger.warn("请打开该文件填写您真实的 appId、clientSecret 与 token 属性后重新运行启动！")
            exitProcess(0)
        }

        val configText = configFile.readText(Charsets.UTF_8)
        val config = try {
            json.decodeFromString(QqBotConfig.serializer(), configText)
        } catch (e: Exception) {
            logger.error("读取或解析配置文件 [$configPath] 失败: ${e.message}", e)
            exitProcess(1)
        }

        try {
            config.validate()
        } catch (e: IllegalArgumentException) {
            logger.error("配置文件参数校验异常: ${e.message}")
            logger.warn("请检查 ${configFile.absolutePath} 中的项配置是否有效合法。")
            exitProcess(1)
        }

        logger.info("成功载入配置文件: AppID=${config.appId}, Sandbox=${config.sandbox}, Intents=${config.intents.joinToString()}")
        if (config.intents.contains("GUILD_MESSAGES") || config.intents.contains("GUILD_AT_MESSAGES")) {
            logger.warn("检测到配置文件中的 intents 包含了频道消息权限 ({})，若当前为标准 QQ 群聊/C2C 机器人且未开通频道，可能会引发 4014 Disallowed Intent 断连。建议移除 GUILD 相关 intents，仅保留 GROUP_AND_C2C_EVENT 等常规事件。", config.intents.joinToString())
        }
        val bot = QqBotImpl(config)

        Runtime.getRuntime().addShutdownHook(Thread {
            logger.info("正在捕捉至 JVM 退出中断指令，准备安全关闭小明机器人...")
            try {
                bot.stop()
            } catch (ignored: Exception) {
            }
        })

        bot.start()

        // 启动控制台交互输入循环
        startConsoleLoop(bot)
    }

    /**
     * 启动控制台 stdin 交互循环。
     *
     * 从标准输入逐行读取用户输入的小明指令，以后台最高权限用户身份执行。
     * 输入 `exit` 或 `stop` 可安全关闭机器人。
     *
     * @param bot 已启动的小明机器人实例
     */
    private fun startConsoleLoop(bot: QqBotImpl) {
        val consoleUser = bot.consoleXiaoMingUser ?: run {
            logger.warn("控制台用户未成功初始化，控制台交互功能不可用。")
            // 无控制台时 fallback 到保持主线程
            Thread.currentThread().join()
            return
        }

        logger.info("控制台交互已就绪，您可以直接输入小明指令（如 #help），输入 exit 可安全退出。")

        val reader = BufferedReader(InputStreamReader(System.`in`, Charsets.UTF_8))
        try {
            while (true) {
                val line = reader.readLine() ?: break  // EOF (如 Ctrl+D)
                val input = line.trim()
                if (input.isEmpty()) continue

                // 内建退出指令
                if (input.equals("exit", ignoreCase = true) || input.equals("stop", ignoreCase = true)) {
                    logger.info("收到控制台退出指令，正在关闭机器人...")
                    bot.stop()
                    exitProcess(0)
                }

                try {
                    val msg = QqMessage(bot, input, System.currentTimeMillis())
                    bot.contactManager.onNextMessageEvent(MessageEvent(consoleUser, msg))
                    bot.scheduler.run(QqReceptionTask(consoleUser, msg))
                } catch (e: Exception) {
                    logger.error("执行控制台指令时发生错误: ${e.message}", e)
                }
            }
        } catch (e: Exception) {
            logger.warn("控制台输入循环中断: ${e.message}")
        }
    }
}

