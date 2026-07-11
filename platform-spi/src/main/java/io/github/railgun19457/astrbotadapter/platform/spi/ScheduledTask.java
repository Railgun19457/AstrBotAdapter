package io.github.railgun19457.astrbotadapter.platform.spi;

public interface ScheduledTask {
    void cancel();

    boolean cancelled();
}
