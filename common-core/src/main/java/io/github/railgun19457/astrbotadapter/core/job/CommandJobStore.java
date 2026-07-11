package io.github.railgun19457.astrbotadapter.core.job;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import io.github.railgun19457.astrbotadapter.protocol.StatusCode;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class CommandJobStore {
    private static final Type JOB_LIST_TYPE = new TypeToken<List<CommandJob>>() {
    }.getType();

    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final ConcurrentHashMap<String, CommandJob> jobs = new ConcurrentHashMap<>();
    private final Path storagePath;

    public CommandJobStore() {
        this(null);
    }

    public CommandJobStore(Path storagePath) {
        this.storagePath = storagePath;
        load();
    }

    public void create(CommandJob job) {
        jobs.put(job.jobId(), job);
        persist();
    }

    public Optional<CommandJob> find(String jobId) {
        return Optional.ofNullable(jobs.get(jobId));
    }

    public Optional<CommandJob> findByDedupeKey(String dedupeKey) {
        if (dedupeKey == null || dedupeKey.isBlank()) {
            return Optional.empty();
        }
        return jobs.values().stream().filter(job -> dedupeKey.equals(job.dedupeKey())).findFirst();
    }

    public void complete(String jobId, String output, int exitCode, long durationMs) {
        jobs.computeIfPresent(jobId, (ignored, job) -> job.withResult("completed", StatusCode.OK, output, exitCode, durationMs));
        persist();
    }

    public void fail(String jobId, StatusCode code, String message) {
        jobs.computeIfPresent(jobId, (ignored, job) -> job.withResult("failed", code, message, 1, 0L));
        persist();
    }

    public List<CommandJob> expired(long now) {
        return jobs.values().stream().filter(job -> job.expiresAt() < now && "pending".equals(job.state())).toList();
    }

    public List<CommandJob> all() {
        return List.copyOf(jobs.values());
    }

    private void load() {
        if (storagePath == null || !Files.exists(storagePath)) {
            return;
        }
        try {
            List<CommandJob> loaded = gson.fromJson(Files.readString(storagePath), JOB_LIST_TYPE);
            if (loaded == null) {
                return;
            }
            for (CommandJob job : loaded) {
                CommandJob normalized = "pending".equals(job.state())
                    ? job.withResult("failed", StatusCode.INTERNAL_ERROR, "job was interrupted by restart", 1, 0L)
                    : job;
                jobs.put(normalized.jobId(), normalized);
            }
            persist();
        } catch (IOException exception) {
            throw new IllegalStateException("failed to load command job store", exception);
        }
    }

    private void persist() {
        if (storagePath == null) {
            return;
        }
        try {
            Files.createDirectories(storagePath.getParent());
            Path tmp = storagePath.resolveSibling(storagePath.getFileName() + ".tmp");
            Files.writeString(tmp, gson.toJson(all()));
            if (Files.exists(storagePath)) {
                Files.copy(storagePath, storagePath.resolveSibling(storagePath.getFileName() + ".bak"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            Files.move(tmp, storagePath, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException exception) {
            throw new IllegalStateException("failed to persist command job store", exception);
        }
    }

    public record CommandJob(
        String jobId,
        String requestId,
        String dedupeKey,
        String serverId,
        long createdAt,
        long expiresAt,
        String state,
        StatusCode statusCode,
        String output,
        int exitCode,
        long durationMs
    ) {
        public CommandJob withResult(String state, StatusCode statusCode, String output, int exitCode, long durationMs) {
            return new CommandJob(jobId, requestId, dedupeKey, serverId, createdAt, expiresAt, state, statusCode, output, exitCode, durationMs);
        }
    }
}
