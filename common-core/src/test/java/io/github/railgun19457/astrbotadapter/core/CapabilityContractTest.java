package io.github.railgun19457.astrbotadapter.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class CapabilityContractTest {
    private final Path contractRoot = Path.of("..", "protocol-contract");

    @Test
    void capabilitiesMatchContractFile() throws IOException {
        JsonArray capabilities = JsonParser.parseString(Files.readString(contractRoot.resolve("capabilities").resolve("capabilities_v1.json"))).getAsJsonArray();
        Set<String> contract = capabilities.asList().stream().map(element -> element.getAsString()).collect(Collectors.toSet());
        Set<String> implementation = Set.of(
            CapabilityNames.CORE_V1,
            CapabilityNames.AUTH_ED25519_V1,
            CapabilityNames.CHAT_V1,
            CapabilityNames.PLAYER_V1,
            CapabilityNames.SERVER_V1,
            CapabilityNames.COMMAND_V1,
            CapabilityNames.AI_V1,
            CapabilityNames.LOG_V1,
            CapabilityNames.CLUSTER_V1,
            CapabilityNames.CONFIG_V1
        );

        assertEquals(contract, implementation);
    }
}
