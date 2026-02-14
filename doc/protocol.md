# AstrbotAdaptor 通信协议规范

## 一、通用规范

### 1.1 认证方式

所有请求（WebSocket连接和REST API）都需要携带Token进行身份验证。

**Token传递方式：**
- WebSocket: 连接时通过URL参数或首次消息携带
- REST API: 通过HTTP Header传递

```
Authorization: Bearer <token>
```

### 1.2 数据格式

- 所有通信均使用 **JSON** 格式
- 字符编码：**UTF-8**
- 时间戳：**Unix毫秒时间戳**

### 1.3 通用响应结构

```json
{
    "code": 0,           // 状态码，0为成功
    "message": "success", // 状态描述
    "data": {},          // 响应数据
    "timestamp": 1706140800000  // 响应时间戳
}
```

### 1.4 错误码定义

| 错误码 | 说明                |
| ------ | ------------------- |
| 0      | 成功                |
| 1001   | 认证失败：Token无效 |
| 1002   | 认证失败：Token过期 |
| 1003   | 认证失败：缺少Token |
| 2001   | 请求参数错误        |
| 2002   | 请求格式错误        |
| 2003   | 缺少必要参数        |
| 3001   | 服务器内部错误      |
| 3002   | 服务不可用          |
| 4001   | 资源不存在          |
| 4002   | 玩家不在线          |
| 4003   | 功能未启用          |
| 5001   | 指令执行失败        |
| 5002   | 指令被过滤          |
| 5003   | 无执行权限          |

---

## 二、WebSocket 消息格式规范

### 2.1 连接建立

**连接地址：**
```
ws://<host>:<port>/ws?token=<auth_token>
```

> **注意**: WebSocket和REST API共用同一端口，通过路径分流：
> - WebSocket: `/ws`
> - REST API: `/api/*`

**连接成功响应：**
```json
{
    "type": "CONNECTION_ACK",
    "data": {
        "sessionId": "uuid-session-id",
        "serverInfo": {
            "name": "MyServer",
            "platform": "Paper",
            "version": "1.21.1"
        }
    },
    "timestamp": 1706140800000
}
```

### 2.2 消息基础结构

**发送消息（Minecraft → Astrbot）：**
```json
{
    "type": "MESSAGE_TYPE",
    "id": "unique-message-id",
    "source": {
        "type": "PLAYER|SERVER|SYSTEM",
        "server": {
            "name": "ServerName",
            "platform": "Paper|Folia|Velocity"
        },
        "player": {
            "uuid": "player-uuid",
            "name": "PlayerName",
            "displayName": "DisplayName"
        }
    },
    "payload": {},
    "timestamp": 1706140800000
}
```

**接收消息（Astrbot → Minecraft）：**
```json
{
    "type": "MESSAGE_TYPE",
    "id": "unique-message-id",
    "target": {
        "type": "PLAYER|BROADCAST|SERVER",
        "playerUuid": "target-player-uuid",
        "playerName": "target-player-name"
    },
    "payload": {},
    "timestamp": 1706140800000
}
```

### 2.3 消息类型定义

| 类型               | 方向 | 说明                |
| ------------------ | ---- | ------------------- |
| `HEARTBEAT`        | 双向 | 心跳消息            |
| `HEARTBEAT_ACK`    | 双向 | 心跳响应            |
| `CONNECTION_ACK`   | 入站 | 连接确认            |
| `CHAT_REQUEST`     | 出站 | AI聊天请求          |
| `CHAT_RESPONSE`    | 入站 | AI聊天响应          |
| `MESSAGE_FORWARD`  | 出站 | 消息转发（MC→外部） |
| `MESSAGE_INCOMING` | 入站 | 外部消息接收        |
| `PLAYER_JOIN`      | 出站 | 玩家加入通知        |
| `PLAYER_QUIT`      | 出站 | 玩家离开通知        |
| `COMMAND_REQUEST`  | 入站 | 指令执行请求        |
| `COMMAND_RESPONSE` | 出站 | 指令执行结果        |
| `STATUS_UPDATE`    | 出站 | 状态更新推送        |
| `ERROR`            | 双向 | 错误消息            |

### 2.4 具体消息格式

#### 2.4.1 心跳消息

**请求：**
```json
{
    "type": "HEARTBEAT",
    "id": "heartbeat-id",
    "timestamp": 1706140800000
}
```

**响应：**
```json
{
    "type": "HEARTBEAT_ACK",
    "id": "heartbeat-id",
    "timestamp": 1706140800000
}
```

#### 2.4.2 AI聊天请求

**请求（群聊）：**
```json
{
    "type": "CHAT_REQUEST",
    "id": "chat-request-id",
    "source": {
        "type": "PLAYER",
        "server": {
            "name": "Survival",
            "platform": "Paper"
        },
        "player": {
            "uuid": "550e8400-e29b-41d4-a716-446655440000",
            "name": "Steve",
            "displayName": "§6Steve"
        }
    },
    "payload": {
        "chatMode": "GROUP",
        "content": "你好，AI！",
        "context": {
            "sessionId": "group-session-id"
        }
    },
    "timestamp": 1706140800000
}
```

**请求（私聊）：**
```json
{
    "type": "CHAT_REQUEST",
    "id": "chat-request-id",
    "source": {
        "type": "PLAYER",
        "server": {
            "name": "Survival",
            "platform": "Paper"
        },
        "player": {
            "uuid": "550e8400-e29b-41d4-a716-446655440000",
            "name": "Steve",
            "displayName": "§6Steve"
        }
    },
    "payload": {
        "chatMode": "PRIVATE",
        "content": "帮我查一下天气",
        "context": {
            "sessionId": "private-550e8400-e29b-41d4-a716-446655440000"
        }
    },
    "timestamp": 1706140800000
}
```

**响应：**
```json
{
    "type": "CHAT_RESPONSE",
    "id": "chat-response-id",
    "replyTo": "chat-request-id",
    "target": {
        "type": "BROADCAST|PLAYER",
        "playerUuid": "550e8400-e29b-41d4-a716-446655440000"
    },
    "payload": {
        "chatMode": "GROUP|PRIVATE",
        "content": "你好！我是AI助手，有什么可以帮助你的？",
        "status": "SUCCESS|ERROR",
        "errorMessage": null
    },
    "timestamp": 1706140800000
}
```

#### 2.4.3 消息转发

**MC→外部：**
```json
{
    "type": "MESSAGE_FORWARD",
    "id": "forward-id",
    "source": {
        "type": "PLAYER",
        "server": {
            "name": "Survival",
            "platform": "Paper"
        },
        "player": {
            "uuid": "550e8400-e29b-41d4-a716-446655440000",
            "name": "Steve",
            "displayName": "§6Steve"
        }
    },
    "payload": {
        "content": "大家好，这是来自服务器的消息！"
    },
    "timestamp": 1706140800000
}
```

**外部→MC：**
```json
{
    "type": "MESSAGE_INCOMING",
    "id": "incoming-id",
    "target": {
        "type": "BROADCAST"
    },
    "payload": {
        "source": {
            "platform": "QQ",
            "userId": "123456",
            "userName": "外部用户"
        },
        "content": "这是来自QQ群的消息！"
    },
    "timestamp": 1706140800000
}
```

#### 2.4.4 玩家进出通知

**玩家加入：**
```json
{
    "type": "PLAYER_JOIN",
    "id": "join-notification-id",
    "source": {
        "type": "SYSTEM",
        "server": {
            "name": "Survival",
            "platform": "Paper"
        }
    },
    "payload": {
        "player": {
            "uuid": "550e8400-e29b-41d4-a716-446655440000",
            "name": "Steve",
            "displayName": "§6Steve"
        },
        "onlineCount": 15,
        "maxPlayers": 100
    },
    "timestamp": 1706140800000
}
```

**玩家离开：**
```json
{
    "type": "PLAYER_QUIT",
    "id": "quit-notification-id",
    "source": {
        "type": "SYSTEM",
        "server": {
            "name": "Survival",
            "platform": "Paper"
        }
    },
    "payload": {
        "player": {
            "uuid": "550e8400-e29b-41d4-a716-446655440000",
            "name": "Steve",
            "displayName": "§6Steve"
        },
        "reason": "QUIT|KICK|TIMEOUT",
        "onlineCount": 14,
        "maxPlayers": 100
    },
    "timestamp": 1706140800000
}
```

#### 2.4.5 指令执行

**请求：**
```json
{
    "type": "COMMAND_REQUEST",
    "id": "command-request-id",
    "payload": {
        "command": "say Hello World",
        "executor": "CONSOLE|PLAYER",
        "playerUuid": null
    },
    "timestamp": 1706140800000
}
```

**响应：**
```json
{
    "type": "COMMAND_RESPONSE",
    "id": "command-response-id",
    "replyTo": "command-request-id",
    "payload": {
        "success": true,
        "command": "say Hello World",
        "output": "[Server] Hello World",
        "executionTime": 12,
        "logs": [
            "[Server] Hello World"
        ],
        "errorCode": null,
        "errorMessage": null
    },
    "timestamp": 1706140800000
}
```

#### 2.4.6 错误消息

```json
{
    "type": "ERROR",
    "id": "error-id",
    "replyTo": "original-message-id",
    "payload": {
        "code": 5001,
        "message": "指令执行失败",
        "details": "Unknown command: xyz"
    },
    "timestamp": 1706140800000
}
```

---

## 三、REST API 规范

### 3.1 基础信息

- **Base URL:** `http://<host>:<port>/api/v1`
- **认证方式:** Header `Authorization: Bearer <token>`
- **Content-Type:** `application/json`

### 3.2 API 列表

| 方法 | 路径                   | 说明                 |
| ---- | ---------------------- | -------------------- |
| GET  | `/server/info`         | 获取服务器信息       |
| GET  | `/server/status`       | 获取服务器状态       |
| GET  | `/players`             | 获取在线玩家列表     |
| GET  | `/players/{uuid}`      | 获取指定玩家详情     |
| GET  | `/players/name/{name}` | 通过名称获取玩家详情 |
| POST | `/command/execute`     | 执行指令             |
| GET  | `/logs`                | 查询日志             |
| GET  | `/health`              | 健康检查             |

### 3.3 API 详细定义

#### 3.3.1 获取服务器信息

**请求：**
```http
GET /api/v1/server/info
Authorization: Bearer <token>
```

**响应：**
```json
{
    "code": 0,
    "message": "success",
    "data": {
        "name": "MyServer",
        "platform": "Paper",
        "platformVersion": "1.21.1-R0.1-SNAPSHOT",
        "minecraftVersion": "1.21.1",
        "motd": "Welcome to MyServer!",
        "maxPlayers": 100,
        "onlineCount": 15,
        "uptime": 86400000,
        "uptimeFormatted": "1d 0h 0m"
    },
    "timestamp": 1706140800000
}
```

#### 3.3.2 获取服务器状态

**请求：**
```http
GET /api/v1/server/status
Authorization: Bearer <token>
```

**响应：**
```json
{
    "code": 0,
    "message": "success",
    "data": {
        "tps": {
            "tps1m": 20.0,
            "tps5m": 19.98,
            "tps15m": 19.95
        },
        "memory": {
            "used": 2048,
            "max": 4096,
            "free": 2048,
            "usagePercent": 50.0
        },
        "worlds": [
            {
                "name": "world",
                "loadedChunks": 256,
                "entities": 1024,
                "players": 10
            }
        ],
        "plugins": {
            "total": 25,
            "enabled": 24
        }
    },
    "timestamp": 1706140800000
}
```

#### 3.3.3 获取在线玩家列表

**请求：**
```http
GET /api/v1/players?page=1&size=20
Authorization: Bearer <token>
```

**查询参数：**
| 参数 | 类型 | 必填 | 默认值 | 说明                |
| ---- | ---- | ---- | ------ | ------------------- |
| page | int  | 否   | 1      | 页码                |
| size | int  | 否   | 20     | 每页数量（最大100） |

**响应：**
```json
{
    "code": 0,
    "message": "success",
    "data": {
        "total": 15,
        "page": 1,
        "size": 20,
        "players": [
            {
                "uuid": "550e8400-e29b-41d4-a716-446655440000",
                "name": "Steve",
                "displayName": "§6Steve",
                "ping": 45,
                "world": "world",
                "gameMode": "SURVIVAL",
                "isOp": false
            }
        ]
    },
    "timestamp": 1706140800000
}
```

#### 3.3.4 获取指定玩家详情

**请求：**
```http
GET /api/v1/players/550e8400-e29b-41d4-a716-446655440000
Authorization: Bearer <token>
```

**响应：**
```json
{
    "code": 0,
    "message": "success",
    "data": {
        "uuid": "550e8400-e29b-41d4-a716-446655440000",
        "name": "Steve",
        "displayName": "§6Steve",
        "ping": 45,
        "health": 20.0,
        "maxHealth": 20.0,
        "foodLevel": 20,
        "level": 30,
        "exp": 0.75,
        "totalExp": 825,
        "gameMode": "SURVIVAL",
        "world": "world",
        "location": {
            "x": 100.5,
            "y": 64.0,
            "z": -200.5
        },
        "isOp": false,
        "isFlying": false,
        "onlineTime": 3600000,
        "onlineTimeFormatted": "1h 0m",
        "firstPlayed": 1700000000000,
        "lastPlayed": 1706140800000
    },
    "timestamp": 1706140800000
}
```

#### 3.3.5 通过名称获取玩家详情

**请求：**
```http
GET /api/v1/players/name/Steve
Authorization: Bearer <token>
```

**响应：** 同 3.3.4

#### 3.3.6 执行指令

**请求：**
```http
POST /api/v1/command/execute
Authorization: Bearer <token>
Content-Type: application/json

{
    "command": "say Hello World",
    "executor": "CONSOLE",
    "async": false
}
```

**请求体参数：**
| 参数       | 类型    | 必填 | 默认值  | 说明                   |
| ---------- | ------- | ---- | ------- | ---------------------- |
| command    | string  | 是   | -       | 要执行的指令（不带/）  |
| executor   | string  | 否   | CONSOLE | 执行者：CONSOLE/PLAYER |
| playerUuid | string  | 条件 | null    | executor为PLAYER时必填 |
| async      | boolean | 否   | false   | 是否异步执行           |

**响应（同步）：**
```json
{
    "code": 0,
    "message": "success",
    "data": {
        "success": true,
        "command": "say Hello World",
        "output": "[Server] Hello World",
        "executionTime": 5,
        "logs": [
            "[Server] Hello World"
        ]
    },
    "timestamp": 1706140800000
}
```

**响应（异步）：**
```json
{
    "code": 0,
    "message": "success",
    "data": {
        "taskId": "task-uuid",
        "status": "QUEUED",
        "command": "say Hello World"
    },
    "timestamp": 1706140800000
}
```

**指令被过滤响应：**
```json
{
    "code": 5002,
    "message": "指令被过滤",
    "data": {
        "command": "op Steve",
        "reason": "Command is blacklisted"
    },
    "timestamp": 1706140800000
}
```

#### 3.3.7 查询日志

**请求：**
```http
GET /api/v1/logs?lines=100&level=INFO&keyword=Steve
Authorization: Bearer <token>
```

**查询参数：**
| 参数      | 类型   | 必填 | 默认值 | 说明                 |
| --------- | ------ | ---- | ------ | -------------------- |
| lines     | int    | 否   | 100    | 返回行数（最大1000） |
| startTime | long   | 否   | -      | 开始时间戳           |
| endTime   | long   | 否   | -      | 结束时间戳           |
| level     | string | 否   | -      | 日志级别过滤         |
| keyword   | string | 否   | -      | 关键词过滤           |

**响应：**
```json
{
    "code": 0,
    "message": "success",
    "data": {
        "total": 100,
        "logs": [
            {
                "timestamp": 1706140800000,
                "level": "INFO",
                "logger": "Server",
                "message": "[Server] Steve joined the game"
            }
        ]
    },
    "timestamp": 1706140800000
}
```

#### 3.3.8 健康检查

**请求：**
```http
GET /api/v1/health
```

**注意：** 此接口不需要认证

**响应：**
```json
{
    "code": 0,
    "message": "success",
    "data": {
        "status": "healthy",
        "version": "1.0.0",
        "uptime": 86400000
    },
    "timestamp": 1706140800000
}
```

---

## 四、WebSocket 连接管理

### 4.1 连接流程

```
客户端                                    服务端
   │                                        │
   │  1. 发起WebSocket连接（携带Token）      │
   │────────────────────────────────────────>│
   │                                        │
   │  2. 验证Token                          │
   │                                        │
   │  3. 返回CONNECTION_ACK                 │
   │<────────────────────────────────────────│
   │                                        │
   │  4. 开始心跳检测                        │
   │<───────────────────────────────────────>│
   │                                        │
   │  5. 正常消息通信                        │
   │<───────────────────────────────────────>│
```

### 4.2 心跳机制

- **心跳间隔：** 30秒
- **超时时间：** 90秒（3次心跳未响应）
- **发起方：** 客户端

### 4.3 重连策略

建议客户端实现：
- 首次重连：立即重连
- 后续重连：指数退避，最大间隔60秒
- 最大重试次数：无限制（持续重连）

### 4.4 断线处理

服务端断开连接时发送：
```json
{
    "type": "DISCONNECT",
    "payload": {
        "reason": "SERVER_SHUTDOWN|AUTH_FAILED|TIMEOUT|ERROR",
        "message": "Server is shutting down"
    },
    "timestamp": 1706140800000
}
```

---

## 五、安全规范

### 5.1 Token规范

- **格式：** 32位随机字符串（字母+数字）
- **生成时机：** 首次启动自动生成
- **存储位置：** config.yml
- **更新方式：** 手动修改配置后重载

### 5.2 请求频率限制

| 接口类型      | 限制           |
| ------------- | -------------- |
| REST API      | 100次/分钟/IP  |
| WebSocket消息 | 60次/分钟/连接 |
| 指令执行      | 10次/分钟      |

### 5.3 输入验证

所有接口需要验证：
- 参数类型正确性
- 参数长度限制
- 特殊字符过滤（指令执行时）

---

## 六、版本兼容性

### 6.1 协议版本

当前协议版本：`v1`

### 6.2 版本协商

WebSocket连接时可通过参数指定版本：
```
ws://<host>:<port>/ws?token=<token>&version=1
```

### 6.3 向后兼容

- 新增字段不会破坏兼容性
- 废弃字段会保留至少2个版本
- 破坏性变更将增加主版本号

---

## 七、代理模式内部通信协议

### 7.1 概述

代理模式（Proxy Mode）用于 Velocity 代理端与后端 Bukkit/Paper/Folia 服务器之间的协作通信。启用代理模式后：

- **后端服务器**不再启动 WebSocket/REST API 服务器，转为通过 Plugin Messaging Channel 向代理端汇报数据
- **Velocity 代理端**负责与 Astrbot 通信（WebSocket/REST），同时管理所有后端服务器的连接和数据聚合
- 所有后端的聊天消息、玩家事件、服务器状态等数据统一通过代理端转发给 Astrbot

**适用场景：** 群组服（BungeeCord/Velocity 代理 + 多个后端服务器）

### 7.2 通信通道

| 项目 | 值 |
| --- | --- |
| Channel ID | `astrbot:proxy` |
| 格式 | `namespace:channel`（现代Minecraft插件消息格式） |
| 最大消息体 | 32768 字节（32KB） |
| 序列化 | JSON via `DataOutputStream.writeUTF()` / `DataInputStream.readUTF()` |
| 编码 | UTF-8 |

### 7.3 认证机制

代理模式采用一次性 Secret 进行身份认证：

1. Velocity 代理端启动时，通过 `SecureRandom` 生成 **32位随机字符串**（大小写字母 + 数字）作为 Secret
2. Secret 在启动日志中显示，管理员将其填入后端服务器的 `config.yml` 中
3. 后端服务器上线后，发送 `AUTH_REQUEST` 消息携带 Secret
4. 代理端验证 Secret，返回 `AUTH_RESPONSE` 确认结果
5. 仅通过认证的后端才能进行后续数据通信
6. 后端每30秒发送一次 `SERVER_INFO_REPORT`，同时作为心跳；如果120秒未收到汇报，代理端自动移除该后端

**认证流程图：**

```
后端 Bukkit/Paper                    Velocity 代理
   │                                    │
   │ ── AUTH_REQUEST ────────────────> │ (携带 secret, serverName, platform, version)
   │                                    │ 验证 secret
   │ <── AUTH_RESPONSE ─────────────── │ (success=true/false, message)
   │                                    │
   │ ── SERVER_INFO_REPORT ──────────> │ (每30秒一次，作为心跳+状态上报)
   │                                    │
```

### 7.4 消息基础结构

所有代理模式内部消息使用统一的 `ProxyMessage` 格式：

```json
{
    "type": "MESSAGE_TYPE",
    "id": "short-uuid",
    "replyTo": "original-request-id",
    "serverName": "backend-server-name",
    "data": {},
    "timestamp": 1706140800000
}
```

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `type` | String | ✅ | 消息类型（见7.5） |
| `id` | String | ✅ | 8位UUID，自动生成 |
| `replyTo` | String | ❌ | 回复的原始消息ID，用于请求-响应关联 |
| `serverName` | String | ❌ | 发送方的服务器名称 |
| `data` | Object | ❌ | 消息负载数据 |
| `timestamp` | Long | ✅ | Unix毫秒时间戳，自动生成 |

**传输帧格式：**

```
┌───────────────────────────────────────┐
│ DataOutputStream.writeUTF(json)       │
│ ┌─────────────────────────────────┐   │
│ │ 2 bytes: UTF length prefix      │   │
│ │ N bytes: JSON string (UTF-8)    │   │
│ └─────────────────────────────────┘   │
└───────────────────────────────────────┘
```

### 7.5 消息类型定义

#### 7.5.1 握手认证

| 类型 | 方向 | 说明 |
| --- | --- | --- |
| `AUTH_REQUEST` | 后端 → 代理 | 携带Secret的认证请求 |
| `AUTH_RESPONSE` | 代理 → 后端 | 认证结果响应 |

#### 7.5.2 数据上报（后端 → 代理）

| 类型 | 方向 | 说明 |
| --- | --- | --- |
| `SERVER_INFO_REPORT` | 后端 → 代理 | 服务器信息（兼心跳），每30秒 |
| `PLAYER_DATA_REPORT` | 后端 → 代理 | 玩家详细数据 |
| `CHAT_MESSAGE_REPORT` | 后端 → 代理 | 玩家聊天消息 |
| `PLAYER_JOIN_REPORT` | 后端 → 代理 | 玩家加入事件 |
| `PLAYER_QUIT_REPORT` | 后端 → 代理 | 玩家离开事件 |
| `COMMAND_RESULT_REPORT` | 后端 → 代理 | 指令执行结果 |
| `LOG_REPORT` | 后端 → 代理 | 日志条目上报 |

#### 7.5.3 指令下发（代理 → 后端）

| 类型 | 方向 | 说明 |
| --- | --- | --- |
| `EXECUTE_COMMAND` | 代理 → 后端 | 执行指令 |
| `SEND_MESSAGE` | 代理 → 后端 | 向指定玩家发送消息 |
| `BROADCAST_MESSAGE` | 代理 → 后端 | 广播消息 |
| `REQUEST_SERVER_INFO` | 代理 → 后端 | 请求服务器信息 |
| `REQUEST_PLAYER_DATA` | 代理 → 后端 | 请求玩家数据 |
| `REQUEST_LOGS` | 代理 → 后端 | 请求日志条目 |

### 7.6 具体消息格式

#### 7.6.1 认证请求 AUTH_REQUEST

**后端 → 代理：**
```json
{
    "type": "AUTH_REQUEST",
    "id": "a1b2c3d4",
    "serverName": "survival",
    "data": {
        "secret": "AbCdEfGh12345678AbCdEfGh12345678",
        "serverName": "survival",
        "platform": "Paper",
        "version": "1.21.1"
    },
    "timestamp": 1706140800000
}
```

#### 7.6.2 认证响应 AUTH_RESPONSE

**代理 → 后端：**
```json
{
    "type": "AUTH_RESPONSE",
    "id": "e5f6g7h8",
    "data": {
        "success": true,
        "message": "认证成功"
    },
    "timestamp": 1706140800000
}
```

认证失败时：
```json
{
    "type": "AUTH_RESPONSE",
    "id": "e5f6g7h8",
    "data": {
        "success": false,
        "message": "Invalid secret"
    },
    "timestamp": 1706140800000
}
```

#### 7.6.3 服务器信息上报 SERVER_INFO_REPORT

**后端 → 代理（每30秒）：**
```json
{
    "type": "SERVER_INFO_REPORT",
    "id": "i9j0k1l2",
    "serverName": "survival",
    "data": {
        "name": "survival",
        "platform": "Paper",
        "version": "1.21.1",
        "motd": "A Minecraft Server",
        "onlineCount": 15,
        "maxPlayers": 100,
        "uptime": 3600000,
        "tps": {
            "tps1m": 19.98,
            "tps5m": 19.95,
            "tps15m": 19.90
        },
        "memory": {
            "used": 1024,
            "max": 4096,
            "free": 3072
        }
    },
    "timestamp": 1706140800000
}
```

#### 7.6.4 玩家数据上报 PLAYER_DATA_REPORT

**后端 → 代理：**
```json
{
    "type": "PLAYER_DATA_REPORT",
    "id": "m3n4o5p6",
    "serverName": "survival",
    "data": {
        "uuid": "550e8400-e29b-41d4-a716-446655440000",
        "name": "Steve",
        "displayName": "§6Steve",
        "health": 20.0,
        "maxHealth": 20.0,
        "level": 30,
        "gameMode": "SURVIVAL",
        "world": "world",
        "ping": 45,
        "isOnline": true,
        "location": {
            "x": 100.5,
            "y": 64.0,
            "z": -200.3
        }
    },
    "timestamp": 1706140800000
}
```

#### 7.6.5 聊天消息上报 CHAT_MESSAGE_REPORT

**后端 → 代理：**
```json
{
    "type": "CHAT_MESSAGE_REPORT",
    "id": "q7r8s9t0",
    "serverName": "survival",
    "data": {
        "playerUuid": "550e8400-e29b-41d4-a716-446655440000",
        "playerName": "Steve",
        "displayName": "§6Steve",
        "message": "你好，AI！"
    },
    "timestamp": 1706140800000
}
```

> 代理端收到后，会将消息转换为标准的 `CHAT_REQUEST` WebSocket消息转发给 Astrbot。

#### 7.6.6 玩家加入上报 PLAYER_JOIN_REPORT

**后端 → 代理：**
```json
{
    "type": "PLAYER_JOIN_REPORT",
    "id": "u1v2w3x4",
    "serverName": "survival",
    "data": {
        "playerUuid": "550e8400-e29b-41d4-a716-446655440000",
        "playerName": "Steve",
        "displayName": "§6Steve",
        "onlineCount": 16,
        "maxPlayers": 100
    },
    "timestamp": 1706140800000
}
```

#### 7.6.7 玩家离开上报 PLAYER_QUIT_REPORT

**后端 → 代理：**
```json
{
    "type": "PLAYER_QUIT_REPORT",
    "id": "y5z6a7b8",
    "serverName": "survival",
    "data": {
        "playerUuid": "550e8400-e29b-41d4-a716-446655440000",
        "playerName": "Steve",
        "displayName": "§6Steve",
        "reason": "Disconnected",
        "onlineCount": 15,
        "maxPlayers": 100
    },
    "timestamp": 1706140800000
}
```

#### 7.6.8 指令执行结果上报 COMMAND_RESULT_REPORT

**后端 → 代理：**
```json
{
    "type": "COMMAND_RESULT_REPORT",
    "id": "c9d0e1f2",
    "replyTo": "original-command-id",
    "serverName": "survival",
    "data": {
        "success": true,
        "command": "say Hello",
        "output": "Command executed",
        "executionTime": 5,
        "logs": ["[Server] Hello"]
    },
    "timestamp": 1706140800000
}
```

> 代理端收到后，会将结果转换为标准的 `COMMAND_RESPONSE` WebSocket消息转发给 Astrbot，并附加 `serverName` 字段。

#### 7.6.9 日志上报 LOG_REPORT

**后端 → 代理：**
```json
{
    "type": "LOG_REPORT",
    "id": "g3h4i5j6",
    "replyTo": "original-request-id",
    "serverName": "survival",
    "data": {
        "logs": ["[INFO] Server started", "[INFO] Player Steve joined"],
        "total": 2
    },
    "timestamp": 1706140800000
}
```

#### 7.6.10 执行指令 EXECUTE_COMMAND

**代理 → 后端：**
```json
{
    "type": "EXECUTE_COMMAND",
    "id": "k7l8m9n0",
    "data": {
        "command": "say Hello from Astrbot",
        "executor": "CONSOLE",
        "playerUuid": null
    },
    "timestamp": 1706140800000
}
```

| 字段 | 说明 |
| --- | --- |
| `command` | 要执行的指令（不含前缀 `/`） |
| `executor` | 执行者类型：`CONSOLE`（控制台）或 `PLAYER`（玩家） |
| `playerUuid` | 当 `executor` 为 `PLAYER` 时，指定执行者的UUID |

#### 7.6.11 发送消息 SEND_MESSAGE

**代理 → 后端：**
```json
{
    "type": "SEND_MESSAGE",
    "id": "o1p2q3r4",
    "data": {
        "playerUuid": "550e8400-e29b-41d4-a716-446655440000",
        "playerName": "Steve",
        "message": "来自AI的回复"
    },
    "timestamp": 1706140800000
}
```

> 后端优先通过 `playerUuid` 查找玩家，未找到则通过 `playerName` 查找。

#### 7.6.12 广播消息 BROADCAST_MESSAGE

**代理 → 后端（发送给所有已认证后端）：**
```json
{
    "type": "BROADCAST_MESSAGE",
    "id": "s5t6u7v8",
    "data": {
        "message": "全服广播消息"
    },
    "timestamp": 1706140800000
}
```

#### 7.6.13 请求服务器信息 REQUEST_SERVER_INFO

**代理 → 后端：**
```json
{
    "type": "REQUEST_SERVER_INFO",
    "id": "w9x0y1z2",
    "timestamp": 1706140800000
}
```

> 后端收到后立即回复一个 `SERVER_INFO_REPORT`。

#### 7.6.14 请求玩家数据 REQUEST_PLAYER_DATA

**代理 → 后端：**
```json
{
    "type": "REQUEST_PLAYER_DATA",
    "id": "a3b4c5d6",
    "data": {
        "playerUuid": "550e8400-e29b-41d4-a716-446655440000"
    },
    "timestamp": 1706140800000
}
```

> 若 `playerUuid` 为空，后端将上报所有在线玩家数据。

#### 7.6.15 请求日志 REQUEST_LOGS

**代理 → 后端：**
```json
{
    "type": "REQUEST_LOGS",
    "id": "e7f8g9h0",
    "data": {
        "lines": 100
    },
    "timestamp": 1706140800000
}
```

> 后端收到后回复 `LOG_REPORT`，`lines` 指定请求的日志行数。

### 7.7 数据流向

#### 7.7.1 聊天消息流（玩家 → AI → 玩家）

```
玩家聊天 (后端)                后端插件              Velocity代理          Astrbot
    │                          │                    │                    │
    │ ── AsyncPlayerChatEvent →│                    │                    │
    │                          │ ── CHAT_MESSAGE_   │                    │
    │                          │    REPORT ────────>│                    │
    │                          │                    │ ── CHAT_REQUEST    │
    │                          │                    │    (WebSocket) ──>│
    │                          │                    │                    │ AI处理
    │                          │                    │ <── CHAT_RESPONSE  │
    │                          │                    │    (WebSocket) ───│
    │                          │ <── SEND_MESSAGE ──│                    │
    │ <── player.sendMessage ──│                    │                    │
```

#### 7.7.2 指令执行流（Astrbot → 后端）

```
Astrbot                       Velocity代理          后端插件              后端服务器
    │                          │                    │                    │
    │ ── COMMAND_REQUEST ────>│                    │                    │
    │    (含 targetServer)     │                    │                    │
    │                          │ ── EXECUTE_COMMAND │                    │
    │                          │    ──────────────>│                    │
    │                          │                    │ ── dispatchCommand │
    │                          │                    │    ──────────────>│
    │                          │                    │ <── result ────── │
    │                          │ <── COMMAND_RESULT │                    │
    │                          │    _REPORT ────── │                    │
    │ <── COMMAND_RESPONSE ── │                    │                    │
    │    (WebSocket)           │                    │                    │
```

#### 7.7.3 玩家事件流（后端 → Astrbot）

```
玩家加入/离开 (后端)           后端插件              Velocity代理          Astrbot
    │                          │                    │                    │
    │ ── PlayerJoinEvent ────>│                    │                    │
    │                          │ ── PLAYER_JOIN_    │                    │
    │                          │    REPORT ────────>│                    │
    │                          │                    │ ── PLAYER_JOIN     │
    │                          │                    │    (WebSocket) ──>│
```

### 7.8 配置说明

#### 7.8.1 后端服务器（Bukkit/Paper/Folia）config.yml

```yaml
# 代理模式（后端服务器配置）
# 启用后，后端不再启动WS/REST API服务器
# 转为通过Plugin Messaging Channel与Velocity代理通信
proxyMode:
  # 是否启用代理模式
  enabled: true
  # Velocity代理端生成的Secret（从代理端启动日志中获取）
  secret: "AbCdEfGh12345678AbCdEfGh12345678"
```

#### 7.8.2 Velocity 代理端 config.yml

```yaml
# 代理桥接模式（Velocity端配置）
# 启用后，Velocity代理端将接受后端服务器的Plugin Messaging连接
# 并将聚合数据转发给Astrbot
proxyBridge:
  # 是否启用代理桥接模式
  enabled: true
```

> **注意：** Velocity 端启用 `proxyBridge` 后仍需正常配置 `connection` 段（host、port、token）以连接 Astrbot。

### 7.9 安全注意事项

1. **Secret 仅在启动时生成**，每次重启 Velocity 代理端都会生成新的 Secret，需重新配置后端
2. **仅通过认证的后端**才能发送/接收数据消息，未认证的消息将被丢弃并记录警告
3. Plugin Messaging Channel 的通信限定在 Velocity 代理与其连接的后端服务器之间，不暴露到外部网络
4. **后端超时清理**：后端超过120秒未发送 `SERVER_INFO_REPORT` 将被自动移除，需重新认证
5. **消息大小限制**：单条消息不超过 32KB，超出将被丢弃

### 7.10 限制与注意事项

1. **Plugin Messaging 依赖在线玩家**：Plugin Messaging Channel 需要至少一个在线玩家才能发送消息。当后端服务器无玩家在线时，消息将无法发送
2. **后端服务器名称**：后端服务器名称取自 `PlatformAdapter.getServerName()`，需要与 Velocity 的 `velocity.toml` 中注册的服务器名称一致
3. **Astrbot 指令路由**：当 Astrbot 发送的 `COMMAND_REQUEST` 包含 `targetServer` 字段时，代理端将指令路由到指定后端执行；若未指定或目标不存在，则在代理端本地执行
