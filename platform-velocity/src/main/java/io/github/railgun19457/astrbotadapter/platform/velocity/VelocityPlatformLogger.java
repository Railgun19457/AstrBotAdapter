package io.github.railgun19457.astrbotadapter.platform.velocity;

import io.github.railgun19457.astrbotadapter.platform.spi.PlatformLogger;
import org.slf4j.Logger;

final class VelocityPlatformLogger implements PlatformLogger {
    private final Logger logger;

    VelocityPlatformLogger(Logger logger) {
        this.logger = logger;
    }

    @Override
    public void info(String message) {
        logger.info(message);
    }

    @Override
    public void warn(String message) {
        logger.warn(message);
    }

    @Override
    public void error(String message, Throwable throwable) {
        logger.error(message, throwable);
    }

    @Override
    public void debug(String message) {
        logger.debug(message);
    }
}
