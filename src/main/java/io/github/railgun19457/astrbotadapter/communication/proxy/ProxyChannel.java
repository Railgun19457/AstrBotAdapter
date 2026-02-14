package io.github.railgun19457.astrbotadapter.communication.proxy;

/**
 * Plugin Messaging Channel constants for proxy-backend communication.
 */
public final class ProxyChannel {

    private ProxyChannel() {}

    /**
     * The plugin messaging channel identifier used between Velocity proxy and backend servers.
     * Format follows the modern "namespace:channel" convention.
     */
    public static final String CHANNEL_ID = "astrbot:proxy";

    /**
     * Maximum size of a single plugin message payload (in bytes).
     * Minecraft limits plugin messages to 1MB, but we use a conservative limit.
     */
    public static final int MAX_PAYLOAD_SIZE = 32768;
}
