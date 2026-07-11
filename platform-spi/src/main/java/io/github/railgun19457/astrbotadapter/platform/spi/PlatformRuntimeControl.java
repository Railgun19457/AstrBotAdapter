package io.github.railgun19457.astrbotadapter.platform.spi;

import java.util.Set;

public interface PlatformRuntimeControl {
    boolean running();

    String modeName();

    String serverId();

    String localFingerprint();

    Set<String> capabilities();

    String createAuthCode(String targetId);

    String listAuthCodes();

    boolean revokeAuthCode(String code);

    String reloadConfig();

    String connectionsStatus();

    String listTrustedKeys();

    boolean revokeTrustedKey(String domain, String keyId);

    String regenerateLocalKey();
}
