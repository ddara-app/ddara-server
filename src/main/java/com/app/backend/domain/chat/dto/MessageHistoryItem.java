package com.app.backend.domain.chat.dto;

import com.app.backend.domain.chat.entity.Message;
import com.app.backend.domain.chat.entity.MessageType;
import com.app.backend.global.util.KstTime;

import java.time.OffsetDateTime;

/** 이력 조회 응답의 메시지 한 건. */
public record MessageHistoryItem(
        Long id,
        Long senderId,
        String senderNickname,
        MessageType type,
        String content,
        boolean deleted,
        OffsetDateTime createdAt
) {
    public static MessageHistoryItem of(Message message, String senderNickname) {
        boolean deleted = message.isDeleted();
        return new MessageHistoryItem(
                message.getId(),
                message.getUserId(),
                senderNickname,
                message.getType(),
                deleted ? null : message.getContent(),
                deleted,
                KstTime.toOffset(message.getCreatedAt()));
    }
}
