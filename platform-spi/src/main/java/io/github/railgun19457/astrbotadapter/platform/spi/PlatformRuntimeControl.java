package io.github.railgun19457.astrbotadapter.platform.spi;

import java.util.Set;

public interface PlatformRuntimeControl {
    boolean running();

    String modeName();

    String serverId();

    String localFingerprint();

    Set<String> capabilities();

    String createPairingCode(String targetId);

    String listPairingCodes();

    boolean revokePairingCode(String pairingId);

    String reloadConfig();

    String connectionsStatus();

    String listTrustedKeys();

    boolean revokeTrustedKey(String domain, String keyId);

    String regenerateLocalKey();
}
