package io.github.railgun19457.astrbotadapter.transport;

public interface TransportLogger {
    void info(String message);

    void warn(String message);

    void error(String message, Throwable throwable);
}
