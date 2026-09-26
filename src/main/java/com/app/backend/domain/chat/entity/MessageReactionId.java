package com.app.backend.domain.chat.entity;

import java.io.Serializable;
import java.util.Objects;

public class MessageReactionId implements Serializable {

    private Long messageId;
    private Long userId;
    private String emoji;

    protected MessageReactionId() {
    }

    public MessageReactionId(Long messageId, Long userId, String emoji) {
        this.messageId = messageId;
        this.userId = userId;
        this.emoji = emoji;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof MessageReactionId that)) {
            return false;
        }
        return Objects.equals(messageId, that.messageId)
                && Objects.equals(userId, that.userId)
                && Objects.equals(emoji, that.emoji);
    }

    @Override
    public int hashCode() {
        return Objects.hash(messageId, userId, emoji);
    }
}
