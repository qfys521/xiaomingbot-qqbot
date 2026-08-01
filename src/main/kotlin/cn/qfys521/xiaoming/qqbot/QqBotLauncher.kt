package cn.qfys521.xiaoming.qqbot

import cn.qfys521.xiaoming.qqbot.config.QqBotConfig
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.io.File
import java.util.concurrent.CountDownLatch
import kotlin.system.exitProcess

/**
 * QQ 官方机器人小明（XiaoMingBot）通用启动器命令行主程序。
 *
 * 启动时默认尝试从工作目录的 `qqbot.json` 加载鉴权配置，若文件不存在，则会自动生成
 * 一份包含完整字段说明的模板配置文件并提示用户填写。
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

        logger.info("成功载入配置文件: AppID=${config.appId}, Sandbox=${config.sandbox}")
        val bot = QqBotImpl(config)

        Runtime.getRuntime().addShutdownHook(Thread {
            logger.info("正在捕捉至 JVM 退出中断指令，准备安全关闭小明机器人...")
            try {
                bot.stop()
            } catch (ignored: Exception) {
            }
        })

        bot.start()

        // 保持主线程正常常驻，避免由于无前台线程致使 JVM 快速终结
        CountDownLatch(1).await()
    }
}
