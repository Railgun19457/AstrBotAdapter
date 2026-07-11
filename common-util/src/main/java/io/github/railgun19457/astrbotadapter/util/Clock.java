package io.github.railgun19457.astrbotadapter.util;

import java.time.Instant;

public interface Clock {
    long nowMillis();

    default Instant now() {
        return Instant.ofEpochMilli(nowMillis());
    }
}
