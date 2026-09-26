package com.app.backend.domain.chat.dto;

import java.time.OffsetDateTime;

/** 채팅방 목록의 방 한 건. */
public record ChatRoomItem(
        Long groupId,
        String groupName,
        String lastMessage,
        OffsetDateTime lastMessageAt,
        long unreadCount
) {
}
