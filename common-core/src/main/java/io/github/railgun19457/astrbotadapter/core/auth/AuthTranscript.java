package io.github.railgun19457.astrbotadapter.core.auth;

import io.github.railgun19457.astrbotadapter.protocol.Role;

import java.util.Collection;
import java.util.Comparator;
import java.util.stream.Collectors;

public final class AuthTranscript {
    private AuthTranscript() {
    }

    public static String clientProof(ClientProof input) {
        StringBuilder builder = header("client-prove", input);
        line(builder, "client_public_key_sha256", input.clientPublicKeySha256());
        line(builder, "expected_server_public_key_sha256", input.expectedServerPublicKeySha256());
        line(builder, "client_nonce", input.clientNonce());
        line(builder, "server_nonce", input.serverNonce());
        line(builder, "challenge_id", input.challengeId());
        line(builder, "challenge_expires_at", input.challengeExpiresAt());
        line(builder, "hello_id", input.helloId());
        line(builder, "challenge_frame_id", input.challengeFrameId());
        line(builder, "client_hello_ts", input.clientHelloTs());
        line(builder, "auth_ts", input.authTs());
        line(builder, "client_capabilities_sha256", input.clientCapabilitiesSha256());
        line(builder, "server_capabilities_sha256", input.serverCapabilitiesSha256());
        return builder.toString();
    }

    public static String serverProof(ServerProof input) {
        StringBuilder builder = header("server-prove", input);
        line(builder, "client_public_key_sha256", input.clientPublicKeySha256());
        line(builder, "server_public_key_sha256", input.serverPublicKeySha256());
        line(builder, "client_nonce", input.clientNonce());
        line(builder, "server_nonce", input.serverNonce());
        line(builder, "challenge_id", input.challengeId());
        line(builder, "challenge_expires_at", input.challengeExpiresAt());
        line(builder, "hello_id", input.helloId());
        line(builder, "challenge_frame_id", input.challengeFrameId());
        line(builder, "auth_frame_id", input.authFrameId());
        line(builder, "auth_result_ts", input.authResultTs());
        line(builder, "client_signature_sha256", input.clientSignatureSha256());
        line(builder, "client_capabilities_sha256", input.clientCapabilitiesSha256());
        line(builder, "server_capabilities_sha256", input.serverCapabilitiesSha256());
        return builder.toString();
    }

    public static String capabilitiesSha256(Collection<String> capabilities) {
        String joined = capabilities == null ? "" : capabilities.stream()
            .sorted(Comparator.naturalOrder())
            .collect(Collectors.joining("\n"));
        return CryptoUtil.sha256Base64Url(joined);
    }

    public static String connectionRole(Role clientRole, Role serverRole) {
        if (clientRole == Role.ASTRBOT && serverRole == Role.STANDALONE) {
            return "astrbot-to-standalone";
        }
        if (clientRole == Role.ASTRBOT && serverRole == Role.VELOCITY) {
            return "astrbot-to-velocity";
        }
        if (clientRole == Role.BACKEND && serverRole == Role.VELOCITY) {
            return "backend-to-velocity";
        }
        return clientRole.wireName() + "-to-" + serverRole.wireName();
    }

    private static StringBuilder header(String direction, CommonFields input) {
        StringBuilder builder = new StringBuilder("ASTRBOT-MC-AUTH-V1\n");
        line(builder, "direction", direction);
        line(builder, "selected_v", input.selectedV());
        line(builder, "client_min_v", input.clientMinV());
        line(builder, "client_max_v", input.clientMaxV());
        line(builder, "connection_role", input.connectionRole());
        line(builder, "client_role", input.clientRole().wireName());
        line(builder, "client_id", input.clientId());
        line(builder, "server_role", input.serverRole().wireName());
        line(builder, "server_id", input.serverId());
        line(builder, "client_key_id", input.clientKeyId());
        line(builder, "server_key_id", input.serverKeyId());
        return builder;
    }

    private static void line(StringBuilder builder, String key, Object value) {
        builder.append(key).append('=').append(safe(value)).append('\n');
    }

    private static String safe(Object value) {
        return value == null ? "" : String.valueOf(value).replace('\n', ' ');
    }

    public interface CommonFields {
        int selectedV();

        int clientMinV();

        int clientMaxV();

        String connectionRole();

        Role clientRole();

        String clientId();

        Role serverRole();

        String serverId();

        String clientKeyId();

        String serverKeyId();
    }

    public record ClientProof(
        int selectedV,
        int clientMinV,
        int clientMaxV,
        String connectionRole,
        Role clientRole,
        String clientId,
        Role serverRole,
        String serverId,
        String clientKeyId,
        String serverKeyId,
        String clientPublicKeySha256,
        String expectedServerPublicKeySha256,
        String clientNonce,
        String serverNonce,
        String challengeId,
        long challengeExpiresAt,
        String helloId,
        String challengeFrameId,
        long clientHelloTs,
        long authTs,
        String clientCapabilitiesSha256,
        String serverCapabilitiesSha256
    ) implements CommonFields {
    }

    public record ServerProof(
        int selectedV,
        int clientMinV,
        int clientMaxV,
        String connectionRole,
        Role clientRole,
        String clientId,
        Role serverRole,
        String serverId,
        String clientKeyId,
        String serverKeyId,
        String clientPublicKeySha256,
        String serverPublicKeySha256,
        String clientNonce,
        String serverNonce,
        String challengeId,
        long challengeExpiresAt,
        String helloId,
        String challengeFrameId,
        String authFrameId,
        long authResultTs,
        String clientSignatureSha256,
        String clientCapabilitiesSha256,
        String serverCapabilitiesSha256
    ) implements CommonFields {
    }
}
