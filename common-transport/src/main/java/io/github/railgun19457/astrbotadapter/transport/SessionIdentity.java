package io.github.railgun19457.astrbotadapter.transport;

import io.github.railgun19457.astrbotadapter.protocol.Role;

public record SessionIdentity(Role role, String id) {
}
