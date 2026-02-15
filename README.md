# AstrBot Adapter

一个用于连接 Minecraft 服务器和 AstrBot 的插件，支持消息互通、服务器状态监测和远程指令执行。


![:name](https://count.getloli.com/@astrbot_adapter?name=astrbot_adapter&theme=minecraft&padding=6&offset=0&align=top&scale=1&pixelated=1&darkmode=auto)

## 特性

- ✅ **消息互通** - 服务器聊天消息转发至 AstrBot，AstrBot 也可以发送消息至服务器，支持发送者信息
- ✅ **Token 鉴权** - 安全的 Token 认证机制
- ✅ **服务器状态监控** - 实时监测玩家信息、TPS、内存使用等
- ✅ **远程指令执行** - 通过外部接口执行服务器指令
- ✅ **玩家事件通知** - 玩家加入/离开通知
- ✅ **游戏内AI聊天** - 支持AI聊天，可区分群聊/私聊

> [!note]
> 孪生项目:[Minecraft适配器](https://github.com/railgun19457/astrbot_plugin_minecraft_adapter)  
> AstrBot插件，用于对接本插件，实现群服互通等功能

## 兼容性

### 支持的平台

| 平台                    | 版本            | 下载                                |
| ----------------------- | --------------- | ----------------------------------- |
| **Bukkit/Paper/Spigot** | 1.20.x - 1.21.x | `AstrbotAdaptor-x.x.x-Bukkit.jar`   |
| **Folia**               | 1.20.x          | `AstrbotAdaptor-x.x.x-Folia.jar`    |
| **Velocity**            | 3.3.x           | `AstrbotAdaptor-x.x.x-Velocity.jar` |

- **Java 版本**: 17+

### 平台说明

- **Bukkit/Paper/Spigot**: 适用于大多数传统 Minecraft 服务器
- **Folia**: 适用于 Paper 的区域化多线程分支，提供更好的多核性能
- **Velocity**: 适用于 Velocity 代理服务器，完整功能须代理端和后端服一起安装插件

## 快速开始

### 独立服务器（非群组服）
1. 安装本插件
2. 从配置文件中获取认证token（或使用指令获取）
3. 在AstrBot插件中添加服务器，并配置服务器地址端口和认证token等信息
> [!note]
> 独立服务器请不要开启`proxyMode`！！！

### velocity群组服
1. 在vc端 和 需要的后端服务器都安装本插件
2. 启动vc端，获取secret值（可查看启动日志 或从插件配置文件夹中的`proxy-secret.txt`文件中获取）
3. 在所有的后端服务器的插件配置中，启用`proxyMode`，并填写secret值，用于插件握手鉴权
4. 从vc端插件配置中，获取认证token，并在AstrBot端插件中添加服务器，填写Velocity地址、端口、token等信息


## 插件配置
配置文件：
- 后端服（Bukkit/Paper/Folia）：`src/main/resources/config-backend.yml`
- 代理端（Velocity）：`src/main/resources/config-velocity.yml`

> 说明：以下为完整字段说明；未特别标注的平台表示两端通用。

### 通用配置说明
| 配置路径 | 默认值 | 类型 | 说明 |
| --- | --- | --- | --- |
| `general.language` | `zh_CN` | string | 插件语言，支持 `zh_CN` / `en_US` |
| `general.debug` | `false` | boolean | 调试模式，开启后输出详细日志 |
| `auth.token` | `""` | string | 认证 Token，留空启动时自动生成 32 位随机 Token |
| `server.host` | `0.0.0.0` | string | WS/REST 监听地址 |
| `server.port` | `8765` | int | WS/REST 监听端口 |
| `server.websocket.enabled` | `true` | boolean | 是否启用 WebSocket 服务 |
| `server.websocket.heartbeatInterval` | `30` | int(秒) | 心跳间隔 |
| `server.websocket.heartbeatTimeout` | `90` | int(秒) | 心跳超时阈值 |
| `server.restapi.enabled` | `true` | boolean | 是否启用 REST API |
| `server.restapi.rateLimit` | `100` | int(次/分钟) | REST 频率限制，`0` 为不限流 |
| `messageForward.enabled` | `true` | boolean | 是否启用聊天消息转发 |
| `messageForward.prefix` | `*` | string | 转发触发前缀（留空表示转发所有消息） |
| `messageForward.stripPrefix` | `true` | boolean | 转发时是否移除前缀 |
| `messageForward.incomingFormat` | `§7[§b{platform}§7] §f{username}§7: §f{content}` | string | 外来消息显示格式，支持 `{platform}` `{username}` `{content}` |
| `aiChat.group.enabled` | `true` | boolean | 是否启用群聊 AI |
| `aiChat.group.prefix` | `@` | string | 群聊 AI 触发前缀 |
| `aiChat.private.enabled` | `true` | boolean | 是否启用私聊 AI |
| `aiChat.private.prefix` | `#` | string | 私聊 AI 触发前缀 |
| `aiChat.private.echoFormat` | `<{player}> {message}` | string | 私聊回显格式，支持 `{player}` `{message}` |
| `aiChat.responseFormat` | `§7[§dAI§7] §f{content}` | string | AI 回复格式，支持 `{content}` |
| `aiChat.thinkingMessage` | `§7[§dAI§7] §e思考中...` | string | AI 思考中提示文案 |
| `aiChat.showThinking` | `true` | boolean | 是否显示思考中提示 |
| `aiChat.timeout` | `60` | int(秒) | AI 请求超时时间 |
| `playerNotification.join.enabled` | `true` | boolean | 是否通知玩家加入 |
| `playerNotification.quit.enabled` | `true` | boolean | 是否通知玩家离开 |
| `commandExecution.enabled` | `true` | boolean | 是否允许远程执行指令 |
| `commandExecution.filterType` | `BLACKLIST` | enum | `NONE` / `BLACKLIST` / `WHITELIST` |
| `commandExecution.commandList` | 见下方 | list[string] | 指令过滤列表，支持 `*` 通配符 |
| `logQuery.enabled` | `true` | boolean | 是否启用日志查询 |
| `logQuery.maxLines` | `1000` | int | 最大返回行数 |
| `logQuery.logFile` | `""` | string | 日志路径（相对服务器根目录），留空默认 `logs/latest.log` |

### 独立服专有配置（Bukkit/Paper/Folia）（Velocity端无以下配置项）

| 配置路径 | 默认值 | 类型 | 说明 |
| --- | --- | --- | --- |
| `updateCheck.enabled` | `true` | boolean | 启动时检查更新 |
| `updateCheck.notifyOps` | `true` | boolean | 有新版本时通知管理员 |
| `proxyMode.enabled` | `false` | boolean | 是否启用 Velocity 代理模式 |
| `proxyMode.secret` | `""` | string | 与 Velocity 通信的鉴权密钥 |

### 特殊说明
- 群组服场景下，后端服务器启用`proxyMode`之后，将不再启动 WebSocket/REST API 服务器，而是通过Plugin Messaging Channel和代理端通信，代理端负责和AstrBot插件通信
- 后端插件启用`proxyMode`之后，大部分通用配置文件将失效，转为直接继承代理端插件配置
- 由于Plugin Messaging Channel的限制，在后端服务器无玩家的情况下，vc端插件将无法和对应后端服务器插件通信，会出现指令无法执行，无法读取到服务器数据等情况

## 游戏内指令

- `/astrbot help` - 显示帮助信息
- `/astrbot reload` - 重载配置文件
- `/astrbot status` - 显示ws/restapi运行状态
- `/astrbot token [show/regen]` - 显示/重新生成认证token
- `/astrbot connections` -显示当前活跃的ws连接

权限：`astrbot.admin` (默认: OP)


## 编译

需要 Java 17+ 和 Maven 3.6+：

运行目录中的 build.bat脚本，会自动构建三端插件

编译后的 jar 文件位于 `release/` 目录：
- `AstrbotAdaptor-x.x.x-Bukkit.jar`
- `AstrbotAdaptor-x.x.x-Folia.jar`
- `AstrbotAdaptor-x.x.x-Velocity.jar`

## 开源协议

MIT License - 详见 [LICENSE](LICENSE)

## 贡献

欢迎提交 Issue 和 Pull Request！
## 更新日志
- v2.0.5
  - 重构后首个正式版
  - 支持前后端插件协作
  - 对应AstrBot插件版本2.0.1+
  
- v2.0.1-beta - v2.0.4-beta
  - 具体更新内容见commit记录

- v2.0.0-beta
  - 重构架构，支持多平台（Bukkit/Folia/Velocity）
  - 新增 Folia 区域化多线程支持
  - 新增 Velocity 代理服务器支持
  - 统一抽象层设计，更好的可扩展性
  - 改进的国际化支持
- v1.0.3
  - 支持AI聊天
  - 添加配置自动校验
- v1.0.2
  - 修复重复转发的bug
  - 精简代码，提高性能
  - 修改游戏内提示为中文
  - 为status命令添加详细连接信息
- v1.0.1
  - 修复bug
  - 修改配置文件
- v1.0.0
  - 实现基本功能

## 支持

- 🐛 [报告问题](https://github.com/railgun19457/AstrBotAdapter/issues)
- 💡 [功能建议](https://github.com/railgun19457/AstrBotAdapter/issues)
- 📧 联系作者：通过 GitHub Issue
