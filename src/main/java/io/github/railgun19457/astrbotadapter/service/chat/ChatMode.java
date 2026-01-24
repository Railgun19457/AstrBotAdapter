package io.github.railgun19457.astrbotadapter.service.chat;

/**
 * 聊天模式枚举
 */
public enum ChatMode {
    
    /** 群聊模式 - 所有玩家可见 */
    GROUP("GROUP"),
    
    /** 私聊模式 - 仅发送者可见 */
    PRIVATE("PRIVATE");

    private final String value;

    ChatMode(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static ChatMode fromString(String value) {
        for (ChatMode mode : values()) {
            if (mode.value.equalsIgnoreCase(value)) {
                return mode;
            }
        }
        return GROUP;
    }
}
