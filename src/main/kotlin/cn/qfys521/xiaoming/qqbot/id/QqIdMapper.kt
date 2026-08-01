package cn.qfys521.xiaoming.qqbot.id

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * QQ 官方 OpenID / GroupOpenID 与小明（Mirai 体系）Long 型 ID 之间的双向映射表与分配组件。
 *
 * 由于 QQ 开放平台官方（v2 体系）使用较长的字符串 OpenID 标识用户与群组，
 * 而小明框架核心及 Mirai 的 `Contact.getCode()` / `User.getCode()` 约束为 `long` 整数类型，
 * 直接使用 `String.hashCode()` 具有极高的哈希碰撞风险。因此通过此映射中心维护唯一、稳定的数字映射关系。
 *
 * 特点：
 * 1. 优先判定纯数字字符串 ID 并转换，对于无法直接转换的字符串 ID 分配自增的非冲突数字 ID。
 * 2. 默认自 `10,000,000,000L`（100 亿）起开始分配，避免与普通真实 QQ 号段重叠碰撞。
 * 3. 支持内存中毫秒级 O(1) 互转 (`toLongId` / `toOpenId`)。
 * 4. 自动支持将映射表落地到 `configurations/qq_id_map.json` 文件持久化，机器人重启不会重新分配或错乱。
 *
 * @author qfys521
 * @since 1.0.0
 */
object QqIdMapper {

    private val logger = LoggerFactory.getLogger(QqIdMapper::class.java)

    // 起始分配 ID（自 100 亿起自增，避免与普通 QQ 号段发生冲突）
    private const val START_ID = 10_000_000_000L

    private val openIdToLongId = ConcurrentHashMap<String, Long>()
    private val longIdToOpenId = ConcurrentHashMap<Long, String>()
    private val nextId = AtomicLong(START_ID)

    private var mapFile: File? = null
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    /**
     * 将开放平台的字符串 ID（OpenID / GroupOpenID / AppID 等）安全映射为唯一的 Long ID。
     *
     * 1. 优先尝试 [String.toLongOrNull]，若本身就是纯数字 ID 且合法则直接返回。
     * 2. 检查现存映射表中是否已分配，若已分配直接返回对应数字 ID。
     * 3. 否则，自增生成一个新的 Long ID，并在内存中建立双向索引，自动向持久化文件刷新。
     *
     * @param openId 待转换的字符串 OpenID
     * @return 映射分配或原有的 Long 数字 ID
     */
    fun toLongId(openId: String): Long {
        if (openId.isBlank()) {
            return 0L
        }
        openId.toLongOrNull()?.let { return it }

        return openIdToLongId.computeIfAbsent(openId) { key ->
            val id = nextId.getAndIncrement()
            longIdToOpenId[id] = key
            saveAsyncQuietly()
            id
        }
    }

    /**
     * 根据 Long 数字 ID 反向查找原始的 OpenID 字符串。
     *
     * @param longId 待查询的 Long 型 ID
     * @return 若存在映射记录则返回原始字符串 OpenID；若未找到对应映射则将 `longId` 直接 `toString()` 返回。
     */
    fun toOpenId(longId: Long): String {
        return longIdToOpenId[longId] ?: longId.toString()
    }

    /**
     * 初始化持久化存储文件路径，并自动从本地读取载入历史 OpenID 映射记录。
     *
     * @param file 持久化映射 JSON 文件（推荐位于 `configurations/qq_id_map.json`）
     */
    @Synchronized
    fun initialize(file: File) {
        mapFile = file
        if (!file.exists()) {
            logger.info("本地暂无历史 OpenID 映射文件 [${file.name}]，启动新映射存储。")
            return
        }

        try {
            val content = file.readText(Charsets.UTF_8)
            val storedMap = json.decodeFromString(IdMapData.serializer(), content)
            var maxId = START_ID

            storedMap.entries.forEach { (openId, longId) ->
                openIdToLongId[openId] = longId
                longIdToOpenId[longId] = openId
                if (longId >= maxId) {
                    maxId = longId + 1
                }
            }

            nextId.set(maxId)
            logger.info("已从 [${file.name}] 成功载入 ${openIdToLongId.size} 条历史 OpenID 映射，下次分配将从 $maxId 开始。")
        } catch (e: Exception) {
            logger.warn("读取历史 OpenID 映射文件出现异常，已重置为新建分配: ${e.message}")
        }
    }

    /**
     * 将当且所有的 OpenID -> Long 映射表同步保存至外部 JSON 文件。
     */
    @Synchronized
    fun save() {
        val file = mapFile ?: return
        try {
            file.parentFile?.mkdirs()
            val data = IdMapData(openIdToLongId)
            file.writeText(json.encodeToString(IdMapData.serializer(), data), Charsets.UTF_8)
        } catch (e: Exception) {
            logger.error("保存 OpenID 映射数据表异常: ${e.message}", e)
        }
    }

    private fun saveAsyncQuietly() {
        try {
            save()
        } catch (ignored: Exception) {
        }
    }

    @Serializable
    private data class IdMapData(
        val entries: Map<String, Long> = emptyMap()
    )
}
