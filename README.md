# AstrBotAdapter

连接 Minecraft 服务器与 AstrBot 的服务端插件。

`v3_2` 支持 Paper、Folia、Fabric 独立服和 Velocity 群组服。用 JDK 25 与 Gradle 9.5 以上版本运行 `gradle build` 构建三个 JAR。

| 平台 | 基线 | 安装位置 | 首次生成的配置 |
| --- | --- | --- | --- |
| Paper / Folia | Paper 1.21.11、26.1.2，Folia 1.21.11 | `build/libs/AstrBotAdapter-Paper-3.0.0-SNAPSHOT.jar` 放入游戏服 `plugins/` | `plugins/AstrBotAdapter/config.yml` |
| Fabric | Minecraft 26.1.2，Java 25，Loader 0.19.3，Fabric API 0.155.2+26.1.2 | `fabric/build/libs/AstrBotAdapter-Fabric-3.0.0-SNAPSHOT.jar` 和 Fabric API 放入游戏服 `mods/` | `config/astrbotadapter/config.properties` |
| Velocity | 4.3.0，Java 25 | `velocity/build/libs/AstrBotAdapter-Velocity-3.0.0-SNAPSHOT.jar` 放入代理的 `plugins/` | `plugins/astrbotadapter/config.properties` |

首次启动后，在对应配置中设置 `node.id` 和监听端口。监听默认为 `127.0.0.1:8765`，路径为 `/astrbot`。同机安装多个实例时，各自使用不同端口。跨机器接入时，用反向代理将 `wss://` 连接转发至此监听地址。

运行 `astrbot pair create astrbot astrbot-main`，把控制台返回的配对 ID 和配对码填入 AstrBot 的「独立服」或「Velocity 群组服」模板。末尾的 `astrbot-main` 应与 AstrBot 的 `client_id` 一致。配对码有效期为 10 分钟。

游戏内默认使用 `*消息` 转发到外部会话。玩家用 `/astrbot ai <问题>` 私聊，也可按权限使用 `/astrbot ai-public <问题>` 公开回复。游戏账号验证使用 `/astrbot bind <验证码>`。

`/astrbot status` 查看运行模式与连接状态，`/astrbot connections` 列出已认证节点及公钥指纹，`/astrbot trust list` 列出信任节点，`/astrbot trust revoke astrbot <client-id>` 撤销信任并断开对应连接。`/astrbot reload` 重载运行时配置。改节点 ID、绑定端口、路径或上游地址后需重启。远程指令默认关闭。Paper/Folia 使用 `commands.remote.enabled` 和 `commands.remote.allow`，Fabric/Velocity 使用 `commands.enabled` 和逗号分隔的 `commands.allow`。

### Velocity 群组服

单装 Velocity 时自动发现已注册子服。游戏端口 Ping 提供可达性、版本、MOTD 和容量。代理提供经过该入口的名单、延迟、所在子服、上下线和换服通知，以及消息转发。指定代理 ID 执行代理自己的命令。

需要子服控制台、性能指标、世界或坐标时，在 Velocity 控制台运行 `astrbot pair create backend <注册名>`，将配对结果写进同名子服配置。Paper/Folia 的 `config.yml` 用下面的结构。Fabric 在 `config.properties` 中使用对应的 `backend.url`、`backend.proxy_id`、`backend.pairing_id`、`backend.pairing_code` 和 `backend.allow_config`。

```yaml
node:
  id: survival
backend:
  url: ws://127.0.0.1:8765/backend
  proxy_id: network
  pairing_id: "<Velocity 签发的配对 ID>"
  pairing_code: "<Velocity 签发的一次性配对码>"
  allow_config: false
```

Velocity 的 `/backend` 和 `/astrbot` 共用监听端口。跨机器时，为两个路径配置 `wss://` 反向代理。子服连上后可去掉配对 ID 和配对码。需要配对到新的代理时，先按需撤销旧信任，并移走该子服的 `upstream-keys/trusted.json`。群组服的聊天、AI、绑定和会话通知由代理负责。子服即使零在线也可查询和执行指令。查询遇到适配器断连时回退到可用的原生查询。

每个子服也可选配原生端口。在 Velocity 的 `config.properties` 中填写与该子服对应的设置。Query 和 RCON 使用 Velocity 注册的子服地址。跨机器的 RCON 使用私网地址，Minecraft 管理接口使用 `wss://`，同机可使用环回 `ws://`。

```properties
backends.survival.query.port=25565
backends.survival.management.url=ws://127.0.0.1:25576
backends.survival.management.secret=<服务端配置的管理接口密钥>
backends.survival.command_source=rcon
backends.survival.rcon.port=25575
backends.survival.rcon.password=<服务端配置的 RCON 密码>
backends.survival.rcon.allow=list,time
```

UDP Query 补充服务器信息和名字名单。官方管理接口通过 `rpc.discover` 确定状态与玩家名单能力。RCON 提供控制台文本输出。`command_source` 默认是 `adapter`，设为 `rcon` 时使用该子服的 `rcon.allow`。执行结果未知时应先核实服务端状态。

子服启用 `backend.allow_config` 后，Velocity 管理员可用 `astrbot config set <子服> <设置> <值>` 写入运行时覆盖配置，或用 `astrbot config reset <子服> <设置>` 恢复本地值。支持 `node.name`、`features.offline_players`、`features.log_query`、`commands.enabled` 和 `commands.allow`，后者用逗号分隔。修改写入子服的 `managed-runtime.properties`。

Paper、Folia、Fabric 默认在玩家离开时保留一份快照，可以用 `features.offline_players` 关闭。Folia TPS/MSPT 显示已采集到的全局区域数据。日志查询需开启 `features.log_query`，限于 `logs/latest.log` 近期内容。

[AstrBot 端 Minecraft 适配器](https://github.com/Railgun19457/astrbot_plugin_minecraft_adapter)

[更新日志](CHANGELOG.md) · [MIT 许可证](LICENSE)
