package com.app.backend.domain.chat.entity;

import java.io.Serializable;
import java.util.Objects;

public class MessageHideId implements Serializable {

    private Long messageId;
    private Long userId;

    protected MessageHideId() {
    }

    public MessageHideId(Long messageId, Long userId) {
        this.messageId = messageId;
        this.userId = userId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof MessageHideId that)) {
            return false;
        }
        return Objects.equals(messageId, that.messageId) && Objects.equals(userId, that.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(messageId, userId);
    }
}
