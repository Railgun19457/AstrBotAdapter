package io.github.railgun19457.astrbotadapter.update;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * GitHub Release 更新检查器
 */
public class GithubReleaseChecker implements UpdateChecker {

    private static final String GITHUB_API_URL = "https://api.github.com/repos/%s/%s/releases/latest";
    private static final int TIMEOUT = 10000; // 10 秒超时

    private final String owner;
    private final String repo;
    private final String currentVersion;
    private final Logger logger;

    private VersionInfo latestVersion;
    private boolean updateAvailable;

    public GithubReleaseChecker(String owner, String repo, String currentVersion, Logger logger) {
        this.owner = owner;
        this.repo = repo;
        this.currentVersion = currentVersion;
        this.logger = logger;
    }

    @Override
    public VersionInfo checkForUpdate() {
        try {
            String apiUrl = String.format(GITHUB_API_URL, owner, repo);
            URL url = new URL(apiUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Accept", "application/vnd.github.v3+json");
            connection.setRequestProperty("User-Agent", "AstrbotAdapter-UpdateChecker");
            connection.setConnectTimeout(TIMEOUT);
            connection.setReadTimeout(TIMEOUT);

            int responseCode = connection.getResponseCode();
            if (responseCode != 200) {
                logger.warning("检查更新失败: HTTP " + responseCode);
                return null;
            }

            StringBuilder response = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
            }

            JsonObject json = JsonParser.parseString(response.toString()).getAsJsonObject();
            return parseRelease(json);

        } catch (Exception e) {
            logger.warning("检查更新时发生错误: " + e.getMessage());
            return null;
        }
    }

    @Override
    public void checkForUpdateAsync(Consumer<VersionInfo> callback) {
        CompletableFuture.supplyAsync(this::checkForUpdate)
                .thenAccept(versionInfo -> {
                    if (versionInfo != null) {
                        latestVersion = versionInfo;
                        updateAvailable = versionInfo.compareToVersion(currentVersion) > 0;
                    }
                    callback.accept(versionInfo);
                })
                .exceptionally(e -> {
                    logger.warning("异步检查更新失败: " + e.getMessage());
                    callback.accept(null);
                    return null;
                });
    }

    /**
     * 解析 GitHub Release 响应
     */
    private VersionInfo parseRelease(JsonObject json) {
        String tagName = json.get("tag_name").getAsString();
        String body = json.has("body") && !json.get("body").isJsonNull() 
                ? json.get("body").getAsString() 
                : "";
        String publishedAt = json.get("published_at").getAsString();

        // 查找下载链接
        String downloadUrl = json.get("html_url").getAsString(); // 默认使用 release 页面
        
        if (json.has("assets") && json.get("assets").isJsonArray()) {
            JsonArray assets = json.getAsJsonArray("assets");
            for (JsonElement asset : assets) {
                JsonObject assetObj = asset.getAsJsonObject();
                String name = assetObj.get("name").getAsString();
                if (name.endsWith(".jar")) {
                    downloadUrl = assetObj.get("browser_download_url").getAsString();
                    break;
                }
            }
        }

        // 解析发布时间
        long publishedTime = System.currentTimeMillis();
        try {
            // 简化的 ISO 8601 解析
            publishedTime = java.time.Instant.parse(publishedAt).toEpochMilli();
        } catch (Exception ignored) {
        }

        latestVersion = new VersionInfo(tagName, downloadUrl, body, publishedTime);
        updateAvailable = latestVersion.compareToVersion(currentVersion) > 0;

        return latestVersion;
    }

    @Override
    public String getCurrentVersion() {
        return currentVersion;
    }

    @Override
    public boolean isUpdateAvailable() {
        return updateAvailable;
    }

    @Override
    public VersionInfo getLatestVersion() {
        return latestVersion;
    }

    /**
     * 获取仓库所有者
     */
    public String getOwner() {
        return owner;
    }

    /**
     * 获取仓库名称
     */
    public String getRepo() {
        return repo;
    }
}
