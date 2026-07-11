package io.github.railgun19457.astrbotadapter.util;

import java.util.Locale;
import java.util.UUID;

public final class Ids {
    private Ids() {
    }

    public static String randomId() {
        return UUID.randomUUID().toString().replace("-", "").toUpperCase(Locale.ROOT);
    }
}
