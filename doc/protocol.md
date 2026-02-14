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
