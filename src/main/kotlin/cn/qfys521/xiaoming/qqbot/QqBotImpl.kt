package cn.qfys521.xiaoming.qqbot

import cn.chuanwise.xiaoming.bot.XiaoMingBot
import cn.chuanwise.xiaoming.bot.XiaoMingBotImpl
import cn.chuanwise.xiaoming.exception.XiaoMingInitializeException
import cn.chuanwise.xiaoming.exception.XiaoMingRuntimeException
import cn.chuanwise.xiaoming.interactor.interactors.AccountInteractors
import cn.chuanwise.xiaoming.interactor.interactors.ConfigurationInteractors
import cn.chuanwise.xiaoming.interactor.interactors.CoreInteractors
import cn.chuanwise.xiaoming.interactor.interactors.GroupInformationInteractors
import cn.chuanwise.xiaoming.interactor.interactors.PermissionInteractors
import cn.chuanwise.xiaoming.interactor.interactors.PluginInteractors
import cn.chuanwise.xiaoming.interactor.interactors.ReceptionistInteractors
import cn.chuanwise.xiaoming.interactor.interactors.ResourceInteractors
import cn.chuanwise.xiaoming.listener.CoreListeners
import cn.qfys521.qqbot.QQBot
import cn.qfys521.qqbot.config.QQBotConfig
import cn.qfys521.qqbot.model.common.ShardConfig
import cn.qfys521.xiaoming.qqbot.config.QqBotConfig
import cn.qfys521.xiaoming.qqbot.console.QqConsoleContact
import cn.qfys521.xiaoming.qqbot.console.QqConsoleUser
import cn.qfys521.xiaoming.qqbot.id.QqIdMapper
import cn.qfys521.xiaoming.qqbot.listener.QqEventListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import net.mamoe.mirai.Bot
import net.mamoe.mirai.contact.ContactList
import net.mamoe.mirai.utils.MiraiInternalApi
import java.io.File
import java.lang.reflect.Proxy
import java.util.Collections

/**
 * QQ 官方机器人适配的 [XiaoMingBot] 核心实现类。
 *
 * 通过直接继承 [XiaoMingBotImpl] 接入小明机器人的完整体系（配置、插件、交互器指令、接待员以及统计报告模块），
 * 同时结合 **[QQBot] 官方异步 Kotlin SDK** 实现对 QQ 群组与单聊等网关消息的收发处理。
 *
 * @property config QQ 机器人接入选项定义
 * @property qqBot 内部集成的 QQBot Kotlin 官方客户端实例
 *
 * @author qfys521
 * @since 1.0.0
 */
class QqBotImpl(
    val config: QqBotConfig
) : XiaoMingBotImpl(createFakeMiraiBot(config.appId)) {

    val qqBot: QQBot = QQBot(
        config = QQBotConfig(
            appId = config.appId,
            clientSecret = config.clientSecret,
            sandbox = config.sandbox,
            intents = QqBotConfig.resolveIntents(config.intents),
            shard = ShardConfig(config.shardId, config.shardCount)
        )
    )

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var gatewayJob: Job? = null

    /**
     * 以必填项简明构造 QQ 官方小明机器人实例。
     *
     * @param appId QQ 机器人 AppID
     * @param clientSecret QQ 机器人应用密钥
     * @param token QQ 机器人访问令牌
     */
    constructor(
        appId: String,
        clientSecret: String,
        token: String = ""
    ) : this(
        QqBotConfig(appId = appId, clientSecret = clientSecret, token = token)
    )

    override fun start() {
        status = XiaoMingBot.Status.ENABLING
        printBanner()

        logger.info("正在启动 QQ 官方小明机器人框架 (appId=${config.appId}) ...")

        // 1. 初始化文件工作路径与内核模块
        initXiaoMingFramework()

        // 2. 注册并建立网关监听
        val eventListener = QqEventListener(this, qqBot)
        eventListener.registerToBot()

        logger.info("正在建立 QQ 开放平台 WebSocket 网关连接...")
        gatewayJob = scope.launch {
            try {
                qqBot.start()
            } catch (e: Exception) {
                logger.error("QQ 网关连接中断或发生错误: ${e.message}", e)
            }
        }

        status = XiaoMingBot.Status.ENABLED
        logger.info("QQ 官方小明机器人启动完毕，可正常进行指令收发 (๑•̀ㅂ•́)و✧！")
    }

    @Synchronized
    override fun stop() {
        if (isDisabled) {
            throw XiaoMingRuntimeException("无法重复停止已经关闭的小明机器人")
        }

        fileSaver.readyToSave(accountManager)
        fileSaver.readyToSave(groupInformationManager)
        fileSaver.readyToSave(configuration)
        fileSaver.readyToSave(statistician)

        status = XiaoMingBot.Status.DISABLING

        logger.info("正在卸载各类小明插件...")
        try {
            pluginManager.pluginHandlers.forEach { handler ->
                pluginManager.unloadPlugin(handler)
            }
        } catch (e: Exception) {
            logger.error("卸载插件时发生异常: ${e.message}", e)
        }

        logger.info("正在关闭 QQ 开放平台客户端链接...")
        gatewayJob?.cancel()
        qqBot.stop()

        QqIdMapper.save()

        statistician.onClose()
        scheduler.stopNow()

        status = XiaoMingBot.Status.DISABLED
        logger.info("QQ 官方小明机器人已成功退出关闭。")
    }

    private fun initXiaoMingFramework() {
        val workingDir = workingDirectory
        configurationDirectory = File(workingDir, "configurations")
        reportDirectory = File(workingDir, "reports")
        logDirectory = File(workingDir, "logs")
        pluginDirectory = File(workingDir, "plugins")
        resourceDirectory = File(workingDir, "resources")

        arrayOf(
            configurationDirectory,
            pluginDirectory,
            resourceDirectory,
            reportDirectory,
            logDirectory
        ).forEach { dir ->
            if (!dir.isDirectory && !dir.mkdirs()) {
                throw XiaoMingInitializeException("初始化时无法创建对应工作路径: ${dir.absolutePath}")
            }
        }

        QqIdMapper.initialize(File(configurationDirectory, "qq_id_map.json"))
        load()

        // 初始化控制台用户（拥有最高权限，可在 stdin 输入指令）
        val consoleContact = QqConsoleContact(this)
        val consoleUser = QqConsoleUser(this, consoleContact)
        consoleUser.receptionist = receptionistManager.getReceptionist(code)
        consoleXiaoMingUser = consoleUser

        // 注册默认内部交互器组
        val im = interactorManager
        im.registerInteractors(PluginInteractors(), null)
        im.registerInteractors(ReceptionistInteractors(), null)
        im.registerInteractors(ResourceInteractors(), null)
        im.registerInteractors(AccountInteractors(), null)
        im.registerInteractors(CoreInteractors(), null)
        im.registerInteractors(ConfigurationInteractors(), null)
        im.registerInteractors(GroupInformationInteractors(), null)
        im.registerInteractors(PermissionInteractors(), null)

        // 注册事件监听器
        eventManager.registerListeners(receptionistManager, null)
        eventManager.registerListeners(CoreListeners(), null)

        try {
            pluginManager.initialize()
        } catch (e: Throwable) {
            logger.error("在加载本地及扩展插件时出现异常", e)
        }
    }

    private fun printBanner() {
        logger.warn(
            """
            
             __   __ _                __  __  _               
             \ \ / /(_)              |  \/  |(_)              
              \ V /  _   __ _   ___  | \  / | _  _ __    __ _ 
               > <  | | / _` | / _ \ | |\/| || || '_ \  / _` |
              / . \ | || (_| || (_) || |  | || || | | || (_| |
             /_/ \_\|_| \__,_| \___/ |_|  |_||_||_| |_| \__, |
                                                         __/ |
                                                        |___/ 
                                                    @${XiaoMingBot.SPONSOR}
            version: ${XiaoMingBot.VERSION} (QQ Official Bot Edition)
            sdk: cn.qfys521:qqbotkt:1.0.0
            github: ${XiaoMingBot.GITHUB}
            appId:  ${config.appId}
            """.trimIndent()
        )
    }

    @OptIn(MiraiInternalApi::class)
    companion object {
        /** 空的群组联系人列表，用于代理返回 */
        private val EMPTY_GROUPS = ContactList<net.mamoe.mirai.contact.Group>()
        /** 空的好友联系人列表，用于代理返回 */
        private val EMPTY_FRIENDS = ContactList<net.mamoe.mirai.contact.Friend>()

        private fun createFakeMiraiBot(appId: String): Bot {
            val numId = QqIdMapper.toLongId(appId)
            return Proxy.newProxyInstance(
                Bot::class.java.classLoader,
                arrayOf(Bot::class.java)
            ) { _, method, args ->
                when (method.name) {
                    "getId" -> numId
                    "isOnline" -> true
                    "getNick", "getName" -> "QQ-Bot-$appId"
                    "login", "close" -> null
                    // 返回空的联系人列表，防止 ContactManagerImpl 调用 .stream() 时 NPE
                    "getGroups" -> EMPTY_GROUPS
                    "getFriends" -> EMPTY_FRIENDS
                    // 单个查询返回 null（上层代码已使用 Optional 包装）
                    "getGroup", "getFriend" -> null
                    "toString" -> "FakeMiraiBot(appId=$appId)"
                    "hashCode" -> numId.hashCode()
                    "equals" -> (args?.getOrNull(0) === method)
                    else -> {
                        when (method.returnType) {
                            Boolean::class.java, java.lang.Boolean::class.java -> false
                            Long::class.java, Long::class.javaObjectType -> 0L
                            Int::class.java, Int::class.javaObjectType -> 0
                            String::class.java -> ""
                            List::class.java, Collection::class.java, MutableList::class.java -> Collections.emptyList<Any>()
                            Map::class.java, MutableMap::class.java -> Collections.emptyMap<Any, Any>()
                            Set::class.java, MutableSet::class.java -> Collections.emptySet<Any>()
                            else -> null
                        }
                    }
                }
            } as Bot
        }
    }
}
