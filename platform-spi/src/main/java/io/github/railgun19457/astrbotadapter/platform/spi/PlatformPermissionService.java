package io.github.railgun19457.astrbotadapter.platform.spi;

public interface PlatformPermissionService {
    boolean hasPermission(String subjectId, String permission);
}
