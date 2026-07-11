package io.github.railgun19457.astrbotadapter.platform.spi;

public interface PlatformLogger {
    void info(String message);

    void warn(String message);

    void error(String message, Throwable throwable);

    default void debug(String message) {
    }
}
