package com.app.backend.domain.chat.dto;

import com.app.backend.domain.chat.entity.Message;
import com.app.backend.domain.chat.entity.MessageType;
import com.app.backend.global.util.KstTime;

import java.time.OffsetDateTime;

/** 채팅 메시지 브로드캐스트/응답 형태 */
public record MessageResponse(
        Long id,
        Long groupId,
        Long senderId,
        String senderNickname,
        MessageType type,
        String content,
        Long shotId,
        String imageUrl,
        String topic,
        OffsetDateTime createdAt
) {
    public static MessageResponse of(Message message, String senderNickname, String imageUrl, String topic) {
        return new MessageResponse(
                message.getId(),
                message.getGroupId(),
                message.getUserId(),
                senderNickname,
                message.getType(),
                message.getContent(),
                message.getShotId(),
                imageUrl,
                topic,
                KstTime.toOffset(message.getCreatedAt()));
    }
}
