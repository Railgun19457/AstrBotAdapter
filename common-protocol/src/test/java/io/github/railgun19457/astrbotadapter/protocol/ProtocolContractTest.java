package io.github.railgun19457.astrbotadapter.protocol;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ProtocolContractTest {
    private final Path contractRoot = Path.of("..", "protocol-contract");

    @Test
    void versionMatchesProtocolConstants() throws IOException {
        JsonObject version = JsonParser.parseString(Files.readString(contractRoot.resolve("version.json"))).getAsJsonObject();

        assertEquals(ProtocolConstants.PROTOCOL_NAME, version.get("protocol_name").getAsString());
        assertEquals(ProtocolConstants.VERSION, version.get("major").getAsInt());
    }

    @Test
    void statusCodesMatchEnumWireNames() throws IOException {
        JsonArray statusCodes = JsonParser.parseString(Files.readString(contractRoot.resolve("errors").resolve("status_codes.json"))).getAsJsonArray();
        Set<String> contract = statusCodes.asList().stream().map(element -> element.getAsString()).collect(Collectors.toSet());
        Set<String> implementation = Arrays.stream(StatusCode.values()).map(StatusCode::wireName).collect(Collectors.toSet());

        assertEquals(contract, implementation);
    }

    @Test
    void goldenServerStatusRequestDecodesAndValidates() throws IOException {
        String json = Files.readString(contractRoot.resolve("examples").resolve("server_status_request.json"));
        Frame<?> frame = new ProtocolCodec().decode(json);

        assertEquals(Ops.SERVER_STATUS, frame.op());
        assertTrue(new FrameValidator().validateEnvelope(frame).valid());
    }

    @Test
    void everySchemaFileIsValidJson() throws IOException {
        try (Stream<Path> files = Files.list(contractRoot.resolve("schema"))) {
            List<Path> schemas = files.filter(path -> path.getFileName().toString().endsWith(".json")).toList();
            assertTrue(schemas.size() >= 10);
            for (Path schema : schemas) {
                JsonElement parsed = JsonParser.parseString(Files.readString(schema));
                assertTrue(parsed.isJsonObject(), schema.toString());
            }
        }
    }

    @Test
    void everyGoldenExampleDecodesValidatesAndRoundTrips() throws IOException {
        ProtocolCodec codec = new ProtocolCodec();
        FrameValidator validator = new FrameValidator();
        try (Stream<Path> files = Files.list(contractRoot.resolve("examples"))) {
            List<Path> examples = files.filter(path -> path.getFileName().toString().endsWith(".json")).toList();
            assertTrue(examples.size() >= plannedOps().size());
            for (Path example : examples) {
                String json = Files.readString(example);
                Frame<?> decoded = codec.decode(json);
                assertTrue(validator.validateEnvelope(decoded).valid(), example.toString());

                String encoded = codec.encode(decoded);
                Frame<?> decodedAgain = codec.decode(encoded);
                assertEquals(decoded.op(), decodedAgain.op(), example.toString());
                assertEquals(JsonParser.parseString(json), JsonParser.parseString(encoded), example.toString());
            }
        }
    }

    @Test
    void everyPlannedOperationHasGoldenExample() throws IOException {
        Set<String> expected = plannedOps();
        Set<String> actual;
        ProtocolCodec codec = new ProtocolCodec();
        try (Stream<Path> files = Files.list(contractRoot.resolve("examples"))) {
            actual = files.filter(path -> path.getFileName().toString().endsWith(".json"))
                .map(path -> {
                    try {
                        return codec.decode(Files.readString(path)).op();
                    } catch (IOException exception) {
                        throw new IllegalStateException(exception);
                    }
                })
                .collect(Collectors.toSet());
        }

        assertTrue(actual.containsAll(expected), "missing examples for " + expected.stream().filter(op -> !actual.contains(op)).toList());
    }

    @Test
    void frameSchemaPinsEnvelopeRequiredFields() throws IOException {
        JsonObject schema = JsonParser.parseString(Files.readString(contractRoot.resolve("schema").resolve("frame.schema.json"))).getAsJsonObject();
        Set<String> required = schema.getAsJsonArray("required").asList().stream().map(element -> element.getAsString()).collect(Collectors.toSet());

        assertTrue(required.contains("v"));
        assertTrue(required.contains("id"));
        assertTrue(required.contains("type"));
        assertTrue(required.contains("op"));
        assertTrue(required.contains("route"));
        assertTrue(required.contains("payload"));
    }

    private Set<String> plannedOps() {
        return Arrays.stream(Ops.class.getDeclaredFields())
            .filter(field -> Modifier.isStatic(field.getModifiers()))
            .filter(field -> field.getType().equals(String.class))
            .map(field -> {
                try {
                    return (String) field.get(null);
                } catch (IllegalAccessException exception) {
                    throw new IllegalStateException(exception);
                }
            })
            .collect(Collectors.toSet());
    }
}
