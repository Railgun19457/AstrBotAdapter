package io.github.railgun19457.astrbotadapter.protocol;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.util.Arrays;
import java.util.Locale;
import java.util.function.Function;

public final class ProtocolCodec {
    private final Gson gson;

    public ProtocolCodec() {
        this.gson = new GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .registerTypeAdapter(Role.class, enumAdapter(Role.values(), Role::wireName))
            .registerTypeAdapter(ServerRole.class, enumAdapter(ServerRole.values(), ServerRole::wireName))
            .registerTypeAdapter(FrameType.class, enumAdapter(FrameType.values(), FrameType::wireName))
            .registerTypeAdapter(TargetScope.class, enumAdapter(TargetScope.values(), TargetScope::wireName))
            .registerTypeAdapter(StatusCode.class, enumAdapter(StatusCode.values(), StatusCode::wireName))
            .create();
    }

    public String encode(Frame<?> frame) {
        return gson.toJson(frame);
    }

    public Frame<JsonElement> decode(String json) {
        try {
            JsonFrame frame = gson.fromJson(json, JsonFrame.class);
            return new Frame<>(
                frame.v(),
                frame.id(),
                frame.type(),
                frame.op(),
                frame.ts(),
                frame.seq(),
                frame.route(),
                frame.origin(),
                frame.target(),
                frame.replyTo(),
                frame.status(),
                frame.payload()
            );
        } catch (JsonParseException exception) {
            throw new ProtocolException("invalid json frame", exception);
        }
    }

    public <T> T decodePayload(JsonElement payload, Class<T> type) {
        return gson.fromJson(payload, type);
    }

    public JsonElement toJsonTree(Object value) {
        return gson.toJsonTree(value);
    }

    public Gson gson() {
        return gson;
    }

    private static <T> TypeAdapter<T> enumAdapter(T[] values, Function<T, String> wireName) {
        return new TypeAdapter<>() {
            @Override
            public void write(JsonWriter out, T value) throws IOException {
                if (value == null) {
                    out.nullValue();
                    return;
                }
                out.value(wireName.apply(value));
            }

            @Override
            public T read(JsonReader in) throws IOException {
                String raw = in.nextString();
                return Arrays.stream(values)
                    .filter(value -> wireName.apply(value).equals(raw))
                    .findFirst()
                    .orElseThrow(() -> new JsonParseException("unknown enum value: " + raw.toLowerCase(Locale.ROOT)));
            }
        };
    }

    private record JsonFrame(
        int v,
        String id,
        FrameType type,
        String op,
        long ts,
        long seq,
        Route route,
        Origin origin,
        Target target,
        String replyTo,
        Status status,
        JsonElement payload
    ) {
    }
}
