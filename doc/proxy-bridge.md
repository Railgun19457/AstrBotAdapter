# Velocity Proxy Bridge Internal Protocol

本文档描述 Velocity 代理端与 Bukkit/Paper/Folia 后端之间的内部 Plugin Messaging 协议。该协议不是 AstrBot/Python 侧对外 API；外部客户端应只依赖 `doc/protocol.md`。

## 1. Scope

Proxy Bridge 用于在 Velocity 上聚合多个后端服务器的数据，并把外部协议中的 `targetServerId` 路由到指定后端。

外部稳定后端 ID 为 Velocity 注册的 server name。REST `servers[].id`、WebSocket `COMMAND_REQUEST.payload.targetServerId` 都使用该 ID。

## 2. Transport

| Item | Value |
| --- | --- |
| Channel | `astrbot:proxy` |
| Payload | Java `DataOutputStream.writeUTF(<json>)` |
| Format | JSON |
| Max payload | `32768` bytes |
| Direction | Backend <-> Velocity |

Minecraft Plugin Messaging 依赖至少一个在线玩家作为转发载体；目标后端无在线玩家时，代理无法向该后端发送消息。

## 3. Configuration

Velocity 端：

```yaml
proxyBridge:
  enabled: true
```

后端服务器：

```yaml
proxyMode:
  enabled: true
  secret: "<velocity-generated-secret>"
```

Velocity 首次启动会生成 `proxy-secret.txt`。后端必须配置相同 secret 才能通过认证。

后端 `proxyMode.enabled=true` 时不会启动对外 REST/WS 服务；Velocity 负责对 AstrBot 暴露 `doc/protocol.md` 中的外部协议。

## 4. Envelope

```json
{
  "type": "MESSAGE_TYPE",
  "id": "a1b2c3d4",
  "replyTo": "original-id",
  "serverName": "survival",
  "data": {},
  "timestamp": 1706140800000
}
```

| Field | Meaning |
| --- | --- |
| `type` | 内部消息类型 |
| `id` | 内部消息 ID，默认 8 字符 UUID 前缀 |
| `replyTo` | 关联请求 ID |
| `serverName` | 后端自报服务器名；路由基准仍是 Velocity sender server name |
| `data` | 类型相关 payload |
| `timestamp` | Unix epoch milliseconds |

Velocity 收到后端消息时使用 Velocity `ServerConnection.getServerInfo().getName()` 作为认证、缓存和路由 key。后端自报 `serverName` 仅用于日志或显示。

## 5. Message Types

| Type | Direction | Purpose |
| --- | --- | --- |
| `AUTH_REQUEST` | Backend -> Velocity | 后端携带 secret 认证 |
| `AUTH_RESPONSE` | Velocity -> Backend | 认证结果 |
| `SERVER_INFO_REPORT` | Backend -> Velocity | 上报版本、人数、TPS、MSPT、内存 |
| `PLAYER_DATA_REPORT` | Backend -> Velocity | 上报玩家详情快照 |
| `CHAT_MESSAGE_REPORT` | Backend -> Velocity | 上报普通聊天用于消息转发 |
| `AI_CHAT_REQUEST_REPORT` | Backend -> Velocity | 上报已处理前缀的 AI 聊天请求 |
| `PLAYER_JOIN_REPORT` | Backend -> Velocity | 上报玩家加入 |
| `PLAYER_QUIT_REPORT` | Backend -> Velocity | 上报玩家离开 |
| `COMMAND_RESULT_REPORT` | Backend -> Velocity | 上报后端命令执行结果 |
| `LOG_REPORT` | Backend -> Velocity | 上报日志查询结果 |
| `SYNC_CONFIG` | Velocity -> Backend | 同步 AI 聊天配置 |
| `EXECUTE_COMMAND` | Velocity -> Backend | 要求后端执行命令 |
| `SEND_MESSAGE` | Velocity -> Backend | 向后端玩家发送消息 |
| `BROADCAST_MESSAGE` | Velocity -> Backend | 要求后端广播消息 |
| `REQUEST_SERVER_INFO` | Velocity -> Backend | 请求后端立即上报服务器信息 |
| `REQUEST_PLAYER_DATA` | Velocity -> Backend | 请求后端上报玩家详情 |
| `REQUEST_LOGS` | Velocity -> Backend | 请求后端上报日志 |

## 6. Authentication Flow

Backend sends `AUTH_REQUEST`：

```json
{
  "type": "AUTH_REQUEST",
  "serverName": "survival",
  "data": {
    "secret": "shared-secret",
    "serverName": "survival",
    "platform": "Paper",
    "version": "1.21.1"
  },
  "timestamp": 1706140800000
}
```

Velocity replies `AUTH_RESPONSE`：

```json
{
  "type": "AUTH_RESPONSE",
  "data": {
    "success": true,
    "message": "认证成功"
  },
  "timestamp": 1706140800000
}
```

认证成功后 Velocity 会发送 `SYNC_CONFIG`。未认证后端发送的非认证消息会被拒绝，并要求重新认证。

## 7. Data Reports

`SERVER_INFO_REPORT.data` 常用字段：

```json
{
  "platform": "Paper",
  "version": "1.21.1",
  "motd": "A Minecraft Server",
  "onlineCount": 15,
  "maxPlayers": 100,
  "uptime": 3600000,
  "mspt": 50.05,
  "tps": {"1m": 19.98, "5m": 19.95, "15m": 19.90},
  "memory": {"used": 512, "total": 1024, "max": 4096}
}
```

`PLAYER_DATA_REPORT.data` 使用外部 REST player detail 的同类字段，如 `uuid/name/displayName/server/ping/world/gameMode/health/location`。

`CHAT_MESSAGE_REPORT.data`：

```json
{
  "playerUuid": "550e8400-e29b-41d4-a716-446655440000",
  "playerName": "Steve",
  "displayName": "Steve",
  "message": "hello"
}
```

`AI_CHAT_REQUEST_REPORT.data`：

```json
{
  "playerUuid": "550e8400-e29b-41d4-a716-446655440000",
  "playerName": "Steve",
  "displayName": "Steve",
  "content": "你好",
  "chatMode": "GROUP"
}
```

`PLAYER_JOIN_REPORT.data` / `PLAYER_QUIT_REPORT.data`：

```json
{
  "playerUuid": "550e8400-e29b-41d4-a716-446655440000",
  "playerName": "Steve",
  "displayName": "Steve",
  "reason": "Disconnected",
  "onlineCount": 15,
  "maxPlayers": 100
}
```

`reason` 仅离开上报需要。

## 8. Command Routing

External client sends WebSocket `COMMAND_REQUEST` with `payload.targetServerId=survival` to Velocity.

Velocity records `COMMAND_REQUEST.id -> WebSocket sessionId`, then sends internal `EXECUTE_COMMAND` to the backend whose Velocity server name is `survival`.

`EXECUTE_COMMAND.data`：

```json
{
  "command": "say Hello",
  "executor": "CONSOLE",
  "playerUuid": null
}
```

Backend replies `COMMAND_RESULT_REPORT`：

```json
{
  "type": "COMMAND_RESULT_REPORT",
  "replyTo": "command-request-id",
  "serverName": "survival",
  "data": {
    "success": true,
    "command": "say Hello",
    "output": "Command executed",
    "executionTime": 5,
    "logs": ["Command executed"]
  },
  "timestamp": 1706140800000
}
```

Velocity converts the result to external WebSocket `COMMAND_RESPONSE`, adds `payload.serverId=<Velocity server name>` and `payload.route=backend`, then sends it only to the original WebSocket session.

REST `POST /api/v1/command/execute` with `targetServerId` only returns a queued acknowledgement; it does not bind to a WebSocket session and therefore does not receive a final async result.

## 9. Lifecycle And Limits

Backends are considered stale after 120 seconds without server info updates and must re-authenticate after cleanup.

Payloads larger than `32768` bytes are dropped.

Velocity can only send messages to a backend when at least one player is connected to that backend.

The external protocol must never expose the internal `serverName` as a route parameter; use `servers[].id` / `targetServerId` instead.
