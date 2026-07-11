package io.github.railgun19457.astrbotadapter.platform.spi;

import java.util.Map;

public record TextOutboundMessage(String content, String plainText, String format, Map<String, Object> attributes) {
}
