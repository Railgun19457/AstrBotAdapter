package io.github.railgun19457.astrbotadapter.communication.protocol;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * Protocol metadata exposed to clients for feature detection.
 */
public final class ProtocolInfo {

    public static final int PROTOCOL_VERSION = 2;
    public static final String API_VERSION = "v1";

    private static final String[] FEATURES = {
            "rest.servers.v2",
            "rest.health",
            "server.mspt",
            "players.detail",
            "players.offline-cache",
            "command.async-result",
            "command.target-server-id",
            "command.ws-session-reply",
            "ws.disconnect"
    };

    private ProtocolInfo() {}

    public static JsonArray featuresJson() {
        JsonArray features = new JsonArray();
        for (String feature : FEATURES) {
            features.add(feature);
        }
        return features;
    }

    public static void addTo(JsonObject target) {
        target.addProperty("protocolVersion", PROTOCOL_VERSION);
        target.addProperty("apiVersion", API_VERSION);
        target.add("features", featuresJson());
    }
}
