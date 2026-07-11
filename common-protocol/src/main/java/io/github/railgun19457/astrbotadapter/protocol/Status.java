package io.github.railgun19457.astrbotadapter.protocol;

public record Status(boolean ok, StatusCode code, String message) {
    public static Status success() {
        return new Status(true, StatusCode.OK, "success");
    }

    public static Status error(StatusCode code, String message) {
        return new Status(false, code, message);
    }
}
