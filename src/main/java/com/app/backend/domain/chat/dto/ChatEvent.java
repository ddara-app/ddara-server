package com.app.backend.domain.chat.dto;

import java.util.List;
import java.util.Map;

/** 브로드캐스트 공통 형식. event로 종류 구분, data에 실제 내용. */
public record ChatEvent(String event, Object data) {

    public static ChatEvent newMessage(Object data) {
        return new ChatEvent("NEW_MESSAGE", data);
    }

    public static ChatEvent messageDeleted(Long messageId) {
        return new ChatEvent("MESSAGE_DELETED", Map.of("messageId", messageId));
    }

    public static ChatEvent reactionUpdated(Long messageId, List<ReactionCount> reactions) {
        return new ChatEvent("REACTION_UPDATED", Map.of("messageId", messageId, "reactions", reactions));
    }
}
