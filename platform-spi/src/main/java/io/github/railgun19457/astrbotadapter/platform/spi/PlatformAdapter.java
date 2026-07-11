package io.github.railgun19457.astrbotadapter.platform.spi;

import java.util.Optional;

public interface PlatformAdapter {
    PlatformInfo platformInfo();

    RuntimeMode runtimeMode();

    PlatformLogger logger();

    PlatformScheduler scheduler();

    PlatformConfigStore configStore();

    PlatformKeyStore keyStore();

    PlatformCommandRegistry commandRegistry();

    PlatformEventSource eventSource();

    Optional<PlatformPlayerService> playerService();

    Optional<PlatformChatService> chatService();

    Optional<PlatformCommandService> commandService();

    Optional<PlatformServerService> serverService();

    Optional<PlatformLogService> logService();

    Optional<PlatformPermissionService> permissionService();

    PlatformTextService textService();
}
