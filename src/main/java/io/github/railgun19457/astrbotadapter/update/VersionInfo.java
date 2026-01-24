package io.github.railgun19457.astrbotadapter.update;

/**
 * 版本信息
 */
public class VersionInfo {

    private final String version;
    private final String downloadUrl;
    private final String releaseNotes;
    private final long publishedAt;

    public VersionInfo(String version, String downloadUrl, String releaseNotes, long publishedAt) {
        this.version = version;
        this.downloadUrl = downloadUrl;
        this.releaseNotes = releaseNotes;
        this.publishedAt = publishedAt;
    }

    public String getVersion() {
        return version;
    }

    public String getDownloadUrl() {
        return downloadUrl;
    }

    public String getReleaseNotes() {
        return releaseNotes;
    }

    public long getPublishedAt() {
        return publishedAt;
    }

    /**
     * 比较版本号
     * @return 正数表示新版本更高，负数表示当前版本更高，0表示相同
     */
    public int compareToVersion(String currentVersion) {
        return compareVersions(this.version, currentVersion);
    }

    /**
     * 比较两个版本号
     */
    public static int compareVersions(String v1, String v2) {
        // 移除 'v' 前缀
        v1 = v1.startsWith("v") ? v1.substring(1) : v1;
        v2 = v2.startsWith("v") ? v2.substring(1) : v2;

        String[] parts1 = v1.split("\\.");
        String[] parts2 = v2.split("\\.");

        int maxLength = Math.max(parts1.length, parts2.length);

        for (int i = 0; i < maxLength; i++) {
            int num1 = i < parts1.length ? parseVersionPart(parts1[i]) : 0;
            int num2 = i < parts2.length ? parseVersionPart(parts2[i]) : 0;

            if (num1 != num2) {
                return num1 - num2;
            }
        }

        return 0;
    }

    /**
     * 解析版本号部分
     */
    private static int parseVersionPart(String part) {
        // 移除非数字后缀 (例如 "1-SNAPSHOT" -> "1")
        String numPart = part.split("-")[0];
        try {
            return Integer.parseInt(numPart);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
