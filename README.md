# XiaoMingBot - QQ Official Bot Adapter (`xiaomingbot-qqbot`)

[![Kotlin](https://img.shields.io/badge/Kotlin-2.4.0-blue.svg?logo=kotlin)](https://kotlinlang.org)
[![JVM Toolchain](https://img.shields.io/badge/JVM-21-orange.svg)](https://adoptium.net)
[![XiaoMingBot](https://img.shields.io/badge/XiaoMingBot-2.x-green.svg)](https://github.com/Chuanwise/xiaomingbot)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](http://www.apache.org/licenses/LICENSE-2.0)

**`xiaomingbot-qqbot`** 是小明机器人（[XiaoMingBot](https://github.com/Chuanwise/xiaomingbot)）框架针对 **腾讯 QQ 开放平台官方异步机器人** (`qqbotkt`) 的深度集成与官方网关适配实现。

参考并延续 [xiaomingbot](https://github.com/Chuanwise/xiaomingbot) 的架构理念，通过声明式适配与响应式事件转发，实现官方 QQ 群聊、C2C 单聊及频道互动对小明内核的无缝驱动。

---

## ✨ 核心特性与优势

1. **原汁原味的小明内核继承**
   - 核心类 `QqBotImpl` 继承自 `XiaoMingBotImpl`，完全复用了小明系统内的配置管理 (`Configuration`)、插件体系 (`PluginManager`)、交互器指令处理 (`InteractorManager`)、接待员会话 (`ReceptionistManager`) 统计上报 (`Statistician`) 等完整组件。
2. **基于官方异步 SDK (`qqbotkt`) 驱动**
   - 底层深度集成了官方异步 Kotlin SDK `qqbotkt:1.0.0`，利用 Ktor Client 异步协同 WebSocket 长连网关，支持断线重连、会话恢复 (Resume) 以及集群水平扩展分片 (`ShardConfig`)。
3. **高稳定性的模型桥接适配**
   - 提供完整的会话与用户模型桥接：
     - `QqContact`: 会话适配器（区分群组与私聊），通过内置的 `Proxy` 对象优雅对接 Mirai-Contact 接口，实现 `sendMessage` 下发。
     - `QqUser`: 发信人实体适配，完整支持小明 `PropertyHandler` 属性包与标签标记，兼容群管理员及权限判定。
     - `QqMessage`: 消息体包装，承载文本、收发时间戳及用于引用和回复处理的 `msg_id`。
     - `QqIdMapper`: **OpenID 双向映射中心**。解决 QQ 官方长字符串 OpenID/GroupOpenID 与 Mirai `long` 型 ID 的兼容问题，通过自 `10,000,000,000L` 起的自增非冲突 ID 替代容易碰撞的 `hashCode()`，并自动落地至 `configurations/qq_id_map.json`。
     - `QqEventListener`: 网关事件路由器，全自动解包转译后派发至 `contactManager` 与主协程调度器 (`scheduler`)。
4. **极简开箱与命令引导**
   - 内置 `QqBotLauncher` 主启动程序，首次执行时会自动在当前工作目录生成模板配置文件 `qqbot.json` 并以友好的控制台提示引导开发者接入。

---

## 🏗️ 整体系统架构

```
+-------------------------------------------------------------------------+
|                              QQ 开放平台网关                             |
+-------------------------------------------------------------------------+
                                    |  ▲
              WebSocket 事件分发    |  | HTTP /v2/messages 发信
                                    ▼  |
+-------------------------------------------------------------------------+
|                  QQBot Official Kotlin SDK (qqbotkt)                    |
+-------------------------------------------------------------------------+
                                    |  ▲
                      事件转发绑定  |  | API 调用 (sendGroupMessage/C2C)
                                    ▼  |
+-------------------------------------------------------------------------+
|                  QqEventListener / QqContact / QqUser                   |
+-------------------------------------------------------------------------+
                                    |
                 派发至 ContactManager / Scheduler.run(...)
                                    ▼
+-------------------------------------------------------------------------+
|                  XiaoMingBot Core (XiaoMingBotImpl)                     |
|    - InteractorManager (命令解析)      - PluginManager (插件体系)          |
|    - ReceptionistManager (接待流程)    - Statistician (行为统计)           |
+-------------------------------------------------------------------------+
```

---

## 🚀 快速上手与配置

### 1. 配置文件 (`qqbot.json`)

系统在首次运行且未检测到配置文件时，自动在工作目录生成 `qqbot.json` 默认样板，格式如下：

```json
{
  "appId": "102000000",
  "clientSecret": "您的官方平台高密鉴权密钥 ClientSecret",
  "token": "可选的 Bot Token 备注",
  "sandbox": false,
  "shardId": 0,
  "shardCount": 1,
  "intents": 1073741824,
  "workingDirectory": "."
}
```

- `appId`: QQ 开放平台申请获得的机器人唯一 ID
- `clientSecret`: 核心高密鉴权凭证（开放平台 v2 认证体系推荐配置）
- `sandbox`: 是否连接开发沙箱网关（生产部署请设为 `false`）
- `shardId` / `shardCount`: 若需要启动集群多节点分片，配置 `[0, N]` 即可生效

### 2. 通过命令行启动器运行

可以使用 Gradle 或打包后的 Jar 启动主程序：

```bash
# 默认加载当前工作目录下的 qqbot.json
java -jar xiaomingbot-qqbot-1.0.0.jar

# 或者指定外部自定义配置文件路径
java -jar xiaomingbot-qqbot-1.0.0.jar /etc/xiaoming/my_qqbot.json
```

### 3. 在 Kotlin/Java 业务代码中内嵌使用

您也可以在自己的工程中直接引用并实例化 `QqBotImpl`：

```kotlin
import cn.qfys521.xiaoming.qqbot.QqBotImpl
import cn.qfys521.xiaoming.qqbot.config.QqBotConfig

fun main() {
    val config = QqBotConfig(
        appId = "102000000",
        clientSecret = "xxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx",
        sandbox = false
    )

    // 创建官方小明机器人实例
    val bot = QqBotImpl(config)

    // 启动小明内核、插件加载并连入 QQ 开放平台网关
    bot.start()

    // 若需停止
    // bot.stop()
}
```

---

## 📦 Maven/Gradle 引入方式

当前项目已配置完整发布规范，可通过 Maven Local 或自定义私服引用：

### Gradle (Kotlin DSL)

```kotlin
dependencies {
    implementation("cn.qfys521:xiaomingbot-qqbot:1.0.0")
    implementation("cn.qfys521:qqbotkt:1.0.0")
}
```

### Gradle (Groovy DSL)

```groovy
dependencies {
    implementation 'cn.qfys521:xiaomingbot-qqbot:1.0.0'
    implementation 'cn.qfys521:qqbotkt:1.0.0'
}
```

### Maven

```xml
<dependency>
    <groupId>cn.qfys521</groupId>
    <artifactId>xiaomingbot-qqbot</artifactId>
    <version>1.0.0</version>
</dependency>
```

---

## 🛠️ 构建与测试

本工程采用 **Gradle 9.x + Kotlin JVM Toolchain 21** 构建：

```bash
# 编译并构建完整 Jar 产物
./gradlew build -x test

# 发布至本地 Maven 仓库 ~/.m2/repository
./gradlew publishToMavenLocal -x test
```

---

## 📄 许可证 (License)

本项目遵循 Apache License 2.0 开源许可证。

```
Copyright 2026 qfys521 & XiaoMingBot Contributors

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0
```
