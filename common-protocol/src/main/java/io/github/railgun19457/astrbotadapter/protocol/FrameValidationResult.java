package io.github.railgun19457.astrbotadapter.protocol;

import java.util.List;

public record FrameValidationResult(boolean valid, List<String> errors) {
    public static FrameValidationResult ok() {
        return new FrameValidationResult(true, List.of());
    }

    public static FrameValidationResult invalid(List<String> errors) {
        return new FrameValidationResult(false, List.copyOf(errors));
    }
}
