package io.github.railgun19457.astrbotadapter.core.config;

import io.github.railgun19457.astrbotadapter.core.FeatureConfig;
import io.github.railgun19457.astrbotadapter.platform.spi.RuntimeMode;

import java.util.List;

public record AdapterConfig(
    RuntimeMode mode,
    String serverId,
    String displayName,
    WebSocketConfig standaloneWebSocket,
    WebSocketConfig astrbotWebSocket,
    WebSocketConfig clusterWebSocket,
    VelocityBackendConfig velocityBackend,
    AuthConfig auth,
    FeatureConfig features,
    ChatConfig chat,
    AiConfig ai,
    CommandPolicyConfig commandPolicy,
    RateLimitConfig rateLimit,
    LocaleConfig locale
) {
    public static AdapterConfig defaults(RuntimeMode mode, String serverId, String displayName) {
        return new AdapterConfig(
            mode,
            serverId,
            displayName,
            new WebSocketConfig(true, "127.0.0.1", 25585, "/astrbot", ""),
            new WebSocketConfig(true, "127.0.0.1", 25585, "/astrbot", ""),
            new WebSocketConfig(true, "0.0.0.0", 25586, "/cluster", ""),
            new VelocityBackendConfig(serverId, "ws://127.0.0.1:25586/cluster"),
            new AuthConfig("server-main", true, "keys", 300000L),
            FeatureConfig.defaults(),
            new ChatConfig(true, "", "[MC] {player}: {message}", "[QQ] {sender}: {message}", true, true),
            new AiConfig(true, "!ai ", true, "!ask "),
            new CommandPolicyConfig(true, List.of(), List.of("stop", "restart", "op", "deop"), false),
            new RateLimitConfig(64, 256, 1048576, 30000, 2),
            new LocaleConfig("zh_CN", true)
        );
    }
}
