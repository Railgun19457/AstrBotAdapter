package io.github.railgun19457.astrbotadapter.core.event.events;

import io.github.railgun19457.astrbotadapter.core.event.Cancellable;
import io.github.railgun19457.astrbotadapter.core.event.Event;

/**
 * 外部指令执行事件
 */
public class ExternalCommandEvent extends Event implements Cancellable {

    private final String command;
    private final String sourceId;
    private final String sourceName;
    
    private boolean cancelled = false;
    private String result = null;
    private boolean success = false;

    public ExternalCommandEvent(String command, String sourceId, String sourceName) {
        this.command = command;
        this.sourceId = sourceId;
        this.sourceName = sourceName;
    }

    public String getCommand() {
        return command;
    }

    public String getSourceId() {
        return sourceId;
    }

    public String getSourceName() {
        return sourceName;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }
}
