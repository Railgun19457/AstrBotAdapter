package io.github.railgun19457.astrbotadapter.protocol;

import io.github.railgun19457.astrbotadapter.util.Strings;

import java.util.ArrayList;
import java.util.List;

public final class FrameValidator {
    public FrameValidationResult validateEnvelope(Frame<?> frame) {
        List<String> errors = new ArrayList<>();

        if (frame == null) {
            return FrameValidationResult.invalid(List.of("frame is required"));
        }

        if (frame.v() != ProtocolConstants.VERSION) {
            errors.add("unsupported protocol version: " + frame.v());
        }
        if (Strings.isBlank(frame.id())) {
            errors.add("id is required");
        }
        if (frame.type() == null) {
            errors.add("type is required");
        }
        if (Strings.isBlank(frame.op())) {
            errors.add("op is required");
        }
        if (frame.ts() <= 0) {
            errors.add("ts must be positive");
        }
        if (frame.seq() <= 0) {
            errors.add("seq must be positive");
        }
        validateRoute(frame.route(), errors);

        if (frame.type() != null) {
            validateTypeSpecificFields(frame, errors);
        }
        validateStatus(frame.status(), errors);
        validateTarget(frame.target(), errors);

        if (errors.isEmpty()) {
            return FrameValidationResult.ok();
        }
        return FrameValidationResult.invalid(errors);
    }

    private void validateRoute(Route route, List<String> errors) {
        if (route == null) {
            errors.add("route is required");
            return;
        }
        validateNodeRef(route.src(), "route.src", errors);
        validateNodeRef(route.dst(), "route.dst", errors);
        if (Strings.isBlank(route.traceId())) {
            errors.add("route.trace_id is required");
        }
    }

    private void validateNodeRef(NodeRef nodeRef, String field, List<String> errors) {
        if (nodeRef == null) {
            errors.add(field + " is required");
            return;
        }
        if (nodeRef.role() == null) {
            errors.add(field + ".role is required");
        }
        if (Strings.isBlank(nodeRef.id())) {
            errors.add(field + ".id is required");
        }
    }

    private void validateTypeSpecificFields(Frame<?> frame, List<String> errors) {
        switch (frame.type()) {
            case EVENT, REQUEST -> {
                if (frame.target() == null) {
                    errors.add("target is required for " + frame.type().wireName());
                }
                if (frame.payload() == null) {
                    errors.add("payload is required for " + frame.type().wireName());
                }
            }
            case RESPONSE -> {
                if (Strings.isBlank(frame.replyTo())) {
                    errors.add("reply_to is required for response");
                }
                if (frame.status() == null) {
                    errors.add("status is required for response");
                }
                if (frame.payload() == null) {
                    errors.add("payload is required for response");
                }
            }
            case ACK -> {
                if (Strings.isBlank(frame.replyTo())) {
                    errors.add("reply_to is required for ack");
                }
                if (frame.payload() == null) {
                    errors.add("payload is required for ack");
                }
            }
            case HEARTBEAT -> {
                if (frame.payload() == null) {
                    errors.add("payload is required for heartbeat");
                }
            }
            case ERROR -> {
                if (frame.status() == null) {
                    errors.add("status is required for error");
                }
            }
            case AUTH_RESULT -> {
                if (frame.payload() == null) {
                    errors.add("payload is required for auth_result");
                }
            }
            case HELLO, CHALLENGE, AUTH -> {
                if (frame.payload() == null) {
                    errors.add("payload is required for " + frame.type().wireName());
                }
            }
        }
    }

    private void validateStatus(Status status, List<String> errors) {
        if (status == null || status.code() == null) {
            return;
        }
        if (status.ok() && status.code() != StatusCode.OK) {
            errors.add("successful status must use code ok");
        }
        if (!status.ok() && status.code() == StatusCode.OK) {
            errors.add("failed status must not use code ok");
        }
    }

    private void validateTarget(Target target, List<String> errors) {
        if (target == null || target.scope() == null) {
            return;
        }
        if (target.scope() == TargetScope.SERVER && Strings.isBlank(target.serverId())) {
            errors.add("target.server_id is required for server scope");
        }
        if (target.scope() != TargetScope.SERVER && !Strings.isBlank(target.serverId())) {
            errors.add("target.server_id is only allowed for server scope");
        }
    }
}
