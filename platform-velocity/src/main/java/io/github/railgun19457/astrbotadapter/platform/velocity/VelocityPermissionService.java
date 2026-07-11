package io.github.railgun19457.astrbotadapter.platform.velocity;

import com.velocitypowered.api.proxy.ProxyServer;
import io.github.railgun19457.astrbotadapter.platform.spi.PlatformPermissionService;

import java.util.UUID;

final class VelocityPermissionService implements PlatformPermissionService {
    private final ProxyServer proxy;

    VelocityPermissionService(ProxyServer proxy) {
        this.proxy = proxy;
    }

    @Override
    public boolean hasPermission(String subjectId, String permission) {
        try {
            return proxy.getPlayer(UUID.fromString(subjectId)).map(player -> player.hasPermission(permission)).orElse(false);
        } catch (IllegalArgumentException exception) {
            return proxy.getPlayer(subjectId).map(player -> player.hasPermission(permission)).orElse(false);
        }
    }
}
